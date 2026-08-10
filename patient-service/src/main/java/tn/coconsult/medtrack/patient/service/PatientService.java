package tn.coconsult.medtrack.patient.service;

import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.accesslog.service.AccesLogService;
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.common.util.ContactNormalizer;
import tn.coconsult.medtrack.common.util.SortUtils;
import tn.coconsult.medtrack.patient.dto.PatientFilter;
import tn.coconsult.medtrack.patient.dto.PatientRequest;
import tn.coconsult.medtrack.patient.dto.PatientResponse;
import tn.coconsult.medtrack.patient.mapper.PatientMapper;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.patient.repository.PatientSpecifications;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.time.Year;

@Service
@RequiredArgsConstructor
public class PatientService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final java.util.Map<String, String> SORTABLE_FIELDS = java.util.Map.of(
            "nom", "nom",
            "prenom", "prenom",
            "dateNaissance", "dateNaissance",
            "numeroDossier", "numeroDossier",
            "createdAt", "createdAt");

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final PatientMapper patientMapper;
    private final AccesLogService accesLogService;

    public PatientResponse create(PatientRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getRole() == Role.MEDECIN) {
            request.setMedecinReferentId(currentUser.getId());
        }
        validateActiveMedecin(request.getMedecinReferentId());
        String normalizedEmail = ContactNormalizer.normalizeEmail(request.getEmail());
        if (patientRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email patient est déjà utilisé");
        }
        request.setEmail(normalizedEmail);

        Patient patient = new Patient();
        applyRequestToEntity(request, patient);
        patient.setNumeroDossier(generateNumeroDossier());
        Patient savedPatient;
        try {
            savedPatient = patientRepository.save(patient);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un patient avec ces informations existe déjà", exception);
        }
        logAcces(savedPatient.getId(), TypeAction.CREATE);
        return patientMapper.toResponse(savedPatient);
    }

    public PageDTO<PatientResponse> search(PatientFilter filter, int page, int size, String sort, String direction) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();

        // Par défaut (archived non fourni ou false), seuls les patients actifs sont listés. Un
        // client ; seul un ADMIN peut explicitement passer archived=true.
        boolean archived = Boolean.TRUE.equals(filter.archived());

        PatientFilter effectiveFilter;
        if (currentUser.getRole() == Role.ADMIN) {
            effectiveFilter = new PatientFilter(filter.nom(), filter.prenom(), filter.email(), filter.medecinReferentId(),
                    filter.telephone(), filter.numeroDossier(), filter.dateNaissanceFrom(), filter.dateNaissanceTo(), archived, filter.q(),
                    filter.sansNumeroDossier(), filter.sansCin());
        } else if (currentUser.getRole() == Role.MEDECIN) {
            effectiveFilter = new PatientFilter(filter.nom(), filter.prenom(), filter.email(), currentUser.getId(),
                    filter.telephone(), filter.numeroDossier(), filter.dateNaissanceFrom(), filter.dateNaissanceTo(), false, filter.q(),
                    filter.sansNumeroDossier(), filter.sansCin());
        } else {
            throw new AccessDeniedException("Accès non autorisé");
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort resolvedSort = SortUtils.resolve(sort, direction, SORTABLE_FIELDS, "createdAt", Sort.Direction.DESC);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, resolvedSort);

        Specification<Patient> spec = PatientSpecifications.build(effectiveFilter);
        Page<Patient> result = patientRepository.findAll(spec, pageRequest);
        return PageDTO.of(result, patientMapper::toResponse);
    }

    public PatientResponse findById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> patientNotFound(id));

        verifierAccesPatient(patient);

        logAcces(patient.getId(), TypeAction.VIEW);

        return patientMapper.toResponse(patient);
    }

    public PatientResponse update(Long id, PatientRequest request) {
        Patient existingPatient = patientRepository.findById(id)
                .orElseThrow(() -> patientNotFound(id));

        verifierAccesPatient(existingPatient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();
        if (currentUser.getRole() == Role.MEDECIN) {
            request.setMedecinReferentId(currentUser.getId());
        }
        if (!java.util.Objects.equals(request.getMedecinReferentId(), existingPatient.getMedecinReferentId())) {
            validateActiveMedecin(request.getMedecinReferentId());
        }
        String normalizedEmail = ContactNormalizer.normalizeEmail(request.getEmail());
        if (patientRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email patient est déjà utilisé");
        }
        request.setEmail(normalizedEmail);

        applyRequestToEntity(request, existingPatient);
        Patient updatedPatient;
        try {
            updatedPatient = patientRepository.save(existingPatient);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un patient avec ces informations existe déjà", exception);
        }
        logAcces(updatedPatient.getId(), TypeAction.UPDATE);
        return patientMapper.toResponse(updatedPatient);
    }

    // Soft delete : un patient n'est jamais supprimé physiquement (conformité dossier médical).
    // Idempotent : ré-archiver ne touche pas archivedAt, pour garder la date du premier archivage.
    public void delete(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> patientNotFound(id));

        verifierAccesPatient(patient);

        if (!patient.isArchived()) {
            patient.setArchived(true);
            patient.setArchivedAt(LocalDateTime.now());
            patientRepository.save(patient);
            logAcces(patient.getId(), TypeAction.ARCHIVE);
        }
    }

    /** Réactive un patient archivé. ADMIN uniquement (voir @PreAuthorize sur le controller). */
    public PatientResponse restore(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> patientNotFound(id));

        patient.setArchived(false);
        patient.setArchivedAt(null);
        Patient saved = patientRepository.save(patient);
        logAcces(saved.getId(), TypeAction.RESTORE);
        return patientMapper.toResponse(saved);
    }

    public boolean isMedecinReferent(Long patientId, Long medecinId) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            return false;
        }
        return medecinId != null && medecinId.equals(patient.getMedecinReferentId());
    }

    private void verifierAccesPatient(Patient patient) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }

        if (currentUser.getRole() == Role.MEDECIN) {
            if (patient.getMedecinReferentId() == null ||
                !patient.getMedecinReferentId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Accès refusé : ce patient ne vous est pas assigné");
            }
            return;
        }

        throw new AccessDeniedException("Accès non autorisé");
    }

    private void logAcces(Long patientId, TypeAction action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();
        accesLogService.enregistrerAcces(currentUser.getId(), TypeRessource.PATIENT, patientId, patientId, action);
    }

    private void applyRequestToEntity(PatientRequest request, Patient patient) {
        patient.setNom(request.getNom());
        patient.setPrenom(request.getPrenom());
        patient.setDateNaissance(request.getDateNaissance());
        patient.setTelephone(ContactNormalizer.normalizeTunisianPhone(request.getTelephone()));
        patient.setEmail(request.getEmail());
        patient.setAdresse(request.getAdresse());
        patient.setSexe(request.getSexe());
        patient.setContactUrgenceNom(request.getContactUrgenceNom());
        patient.setContactUrgenceTelephone(ContactNormalizer.normalizeTunisianPhone(request.getContactUrgenceTelephone()));
        patient.setCin(request.getCin());
        patient.setNumeroAssurance(request.getNumeroAssurance());
        patient.setMedecinReferentId(request.getMedecinReferentId());
    }

    private void validateActiveMedecin(Long medecinId) {
        if (medecinId == null) {
            return;
        }
        User medecin = userRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin référent introuvable"));
        if (medecin.getRole() != Role.MEDECIN) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "L'utilisateur sélectionné n'est pas un médecin");
        }
        if (Boolean.FALSE.equals(medecin.getActif())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Le médecin référent est inactif");
        }
    }

    private ResponseStatusException patientNotFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient non trouvé avec l'id: " + id);
    }

    // UNIQUE en base reste le garde-fou en cas de créations concurrentes (collision -> retry).
    private String generateNumeroDossier() {
        String prefix = "PAT-" + Year.now().getValue() + "-";
        int nextSequence = patientRepository
                .findTopByNumeroDossierStartingWithOrderByNumeroDossierDesc(prefix)
                .map(last -> Integer.parseInt(last.getNumeroDossier().substring(prefix.length())) + 1)
                .orElse(1);
        return prefix + String.format("%05d", nextSequence);
    }
}
