package tn.coconsult.medtrack.prescription.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.accesslog.service.AccesLogService;
import tn.coconsult.medtrack.common.dto.ConsultationSummaryResponse;
import tn.coconsult.medtrack.common.dto.MedecinSummaryResponse;
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.common.util.SearchTerms;
import tn.coconsult.medtrack.common.util.SortUtils;
import tn.coconsult.medtrack.medicalrecord.model.Consultation;
import tn.coconsult.medtrack.medicalrecord.model.MedicalRecord;
import tn.coconsult.medtrack.medicalrecord.repository.ConsultationRepository;
import tn.coconsult.medtrack.medicalrecord.repository.MedicalRecordRepository;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.prescription.client.ConsultationSummaryClient;
import tn.coconsult.medtrack.prescription.client.MedecinSummaryClient;
import tn.coconsult.medtrack.prescription.client.PatientDetailsClient;
import tn.coconsult.medtrack.prescription.client.PatientReferentClient;
import tn.coconsult.medtrack.prescription.dto.PatientDetailsResponse;
import tn.coconsult.medtrack.prescription.dto.PrescriptionFilter;
import tn.coconsult.medtrack.prescription.dto.PrescriptionRequest;
import tn.coconsult.medtrack.prescription.dto.PrescriptionResponse;
import tn.coconsult.medtrack.prescription.mapper.PrescriptionMapper;
import tn.coconsult.medtrack.prescription.model.Prescription;
import tn.coconsult.medtrack.prescription.repository.PrescriptionRepository;
import tn.coconsult.medtrack.prescription.repository.PrescriptionSpecifications;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final java.util.Map<String, String> SORTABLE_FIELDS = java.util.Map.of(
            "medicament", "medicament",
            "createdAt", "createdAt");

    private final PrescriptionRepository prescriptionRepository;
    private final ConsultationRepository consultationRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ConsultationSummaryClient consultationSummaryClient;
    private final PatientReferentClient patientReferentClient;
    private final PatientDetailsClient patientDetailsClient;
    private final MedecinSummaryClient medecinSummaryClient;
    private final OrdonnancePdfGenerator ordonnancePdfGenerator;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AccesLogService accesLogService;
    private final PrescriptionMapper prescriptionMapper;

    /** Liste non paginée des prescriptions d'une consultation (volume faible par consultation). Exclut les archivées par défaut. */
    public List<PrescriptionResponse> listForConsultation(Long consultationId, Boolean archivedParam) {
        ConsultationSummaryResponse summary = consultationSummaryClient.getSummary(consultationId);
        requireAccess(summary.patientId());
        boolean archived = Boolean.TRUE.equals(archivedParam);
        Specification<Prescription> spec = PrescriptionSpecifications.hasConsultationId(consultationId)
                .and(PrescriptionSpecifications.hasArchived(archived));
        List<Prescription> result = prescriptionRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        return enrichBatch(result);
    }

    public byte[] generateOrdonnance(Long consultationId) {
        ConsultationSummaryResponse summary = consultationSummaryClient.getSummary(consultationId);
        requireAccess(summary.patientId());

        PatientDetailsResponse patient = patientDetailsClient.getById(summary.patientId());
        MedecinSummaryResponse medecin = medecinSummaryClient.getSummary(summary.medecinId());

        // Archivées exclues et tri chronologique ASC, l'inverse de la liste écran (récentes d'abord).
        Specification<Prescription> spec = PrescriptionSpecifications.hasConsultationId(consultationId)
                .and(PrescriptionSpecifications.hasArchived(false));
        List<Prescription> prescriptions = prescriptionRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "createdAt"));

        byte[] pdf = ordonnancePdfGenerator.generate(medecin, patient, prescriptions, LocalDate.now());
        logAcces(TypeRessource.PRESCRIPTION, consultationId, summary.patientId(), TypeAction.PRINT);
        return pdf;
    }

    public PrescriptionResponse create(Long consultationId, PrescriptionRequest request, boolean renewal) {
        ConsultationSummaryResponse summary = consultationSummaryClient.getSummary(consultationId);
        requireAccess(summary.patientId());
        Prescription prescription = new Prescription();
        prescription.setConsultationId(consultationId);
        applyRequestToEntity(request, prescription);
        Prescription saved = prescriptionRepository.save(prescription);
        PrescriptionResponse response = enrichOne(saved);
        logAcces(TypeRessource.PRESCRIPTION, saved.getId(), summary.patientId(),
                renewal ? TypeAction.RENEW : TypeAction.CREATE);
        return response;
    }

    public PageDTO<PrescriptionResponse> search(PrescriptionFilter filter, int page, int size, String sort, String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort resolvedSort = SortUtils.resolve(sort, direction, SORTABLE_FIELDS, "createdAt", Sort.Direction.DESC);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, resolvedSort);

        Specification<Prescription> spec = buildScopedSpec(filter);
        Page<Prescription> result = prescriptionRepository.findAll(spec, pageRequest);
        List<PrescriptionResponse> content = enrichBatch(result.getContent());
        return new PageDTO<>(content, result.getNumber(), result.getSize(), result.getTotalPages(), result.getTotalElements());
    }

    public long count(PrescriptionFilter filter) {
        return prescriptionRepository.count(buildScopedSpec(filter));
    }

    public PrescriptionResponse getById(Long id) {
        PrescriptionResponse response = enrichOne(findOrThrow(id));
        if (response.getPatientId() != null) {
            logAcces(TypeRessource.PRESCRIPTION, id, response.getPatientId(), TypeAction.VIEW);
        }
        return response;
    }

    public PrescriptionResponse update(Long id, PrescriptionRequest request) {
        Prescription prescription = findOrThrow(id);
        applyRequestToEntity(request, prescription);
        Prescription saved = prescriptionRepository.save(prescription);
        PrescriptionResponse response = enrichOne(saved);
        if (response.getPatientId() != null) {
            logAcces(TypeRessource.PRESCRIPTION, saved.getId(), response.getPatientId(), TypeAction.UPDATE);
        }
        return response;
    }

    /** Soft delete : donnée médicale, jamais supprimée physiquement (même traitement que Consultation). */
    public void delete(Long id) {
        Prescription prescription = findOrThrow(id);
        if (!prescription.isArchived()) {
            PrescriptionResponse response = enrichOne(prescription);
            prescription.setArchived(true);
            prescription.setArchivedAt(LocalDateTime.now());
            prescriptionRepository.save(prescription);
            if (response.getPatientId() != null) {
                logAcces(TypeRessource.PRESCRIPTION, id, response.getPatientId(), TypeAction.ARCHIVE);
            }
        }
    }

    /** Réactive une prescription archivée. Idempotent. Même contrôle d'accès que create/update/delete. */
    public PrescriptionResponse restore(Long id) {
        Prescription prescription = findOrThrow(id);
        if (prescription.isArchived()) {
            prescription.setArchived(false);
            prescription.setArchivedAt(null);
            prescription = prescriptionRepository.save(prescription);
            PrescriptionResponse response = enrichOne(prescription);
            if (response.getPatientId() != null) {
                logAcces(TypeRessource.PRESCRIPTION, id, response.getPatientId(), TypeAction.RESTORE);
            }
            return response;
        }
        return enrichOne(prescription);
    }

    public boolean isMedecinReferentForPrescription(Long prescriptionId, Long medecinId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId).orElse(null);
        if (prescription == null) {
            return false;
        }
        ConsultationSummaryResponse summary;
        try {
            summary = consultationSummaryClient.getSummary(prescription.getConsultationId());
        } catch (ResponseStatusException notFound) {
            return false;
        }
        return patientReferentClient.isReferent(summary.patientId(), medecinId);
    }

    private void requireAccess(Long patientId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() == Role.MEDECIN && patientReferentClient.isReferent(patientId, currentUser.getId())) {
            return;
        }
        throw new AccessDeniedException("Accès refusé : ce patient ne vous est pas assigné");
    }

    private void logAcces(TypeRessource type, Long ressourceId, Long patientId, TypeAction action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();
        accesLogService.enregistrerAcces(currentUser.getId(), type, ressourceId, patientId, action);
    }

    private Prescription findOrThrow(Long id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription introuvable"));
    }

    private void applyRequestToEntity(PrescriptionRequest request, Prescription prescription) {
        prescription.setMedicament(request.getMedicament());
        prescription.setPosologie(request.getPosologie());
        prescription.setDureeJours(request.getDureeJours());
        prescription.setInstructions(request.getInstructions());
        prescription.setRenouvelable(request.isRenouvelable());
    }

    private Specification<Prescription> buildScopedSpec(PrescriptionFilter filter) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();

        Specification<Prescription> spec = PrescriptionSpecifications.build(filter);

        if (filter.patientId() != null || filter.medecinId() != null || filter.dateFrom() != null || filter.dateTo() != null) {
            List<Long> consultationIds = resolveConsultationIdsForFilter(
                    filter.patientId(), filter.medecinId(), filter.dateFrom(), filter.dateTo());
            Specification<Prescription> filterSpec = PrescriptionSpecifications.hasConsultationIdIn(consultationIds);
            spec = (spec == null) ? filterSpec : spec.and(filterSpec);
        }

        Specification<Prescription> qSpec = buildSearchTermSpec(filter.q());
        if (qSpec != null) {
            spec = (spec == null) ? qSpec : spec.and(qSpec);
        }

        Specification<Prescription> statutSpec = buildStatutCalculeSpec(filter.statutCalcule());
        if (statutSpec != null) {
            spec = (spec == null) ? statutSpec : spec.and(statutSpec);
        }

        if (currentUser.getRole() == Role.MEDECIN) {
            List<Long> referentPatientIds = patientRepository
                    .findAll(hasMedecinReferentId(currentUser.getId()))
                    .stream().map(Patient::getId).toList();
            List<Long> allowedConsultationIds = resolveConsultationIdsForPatients(referentPatientIds);
            Specification<Prescription> scopeSpec = PrescriptionSpecifications.hasConsultationIdIn(allowedConsultationIds);
            spec = (spec == null) ? scopeSpec : spec.and(scopeSpec);
        }

        return spec;
    }

    private Specification<Prescription> buildStatutCalculeSpec(String statutCalcule) {
        if (!StringUtils.hasText(statutCalcule)) {
            return null;
        }
        List<Long> ids = switch (statutCalcule) {
            case "ACTIVE" -> prescriptionRepository.findActiveIds();
            case "EXPIREE" -> prescriptionRepository.findExpiredIds();
            default -> null;
        };
        return ids == null ? null : PrescriptionSpecifications.hasIdIn(ids);
    }

    /**
     * Une recherche à plusieurs mots ("Haddad Hatem") doit trouver les prescriptions d'un patient
     * dont le nom et le prénom matchent chacun un mot différent : chaque mot est résolu
     * indépendamment, tous les mots devant matcher (AND) pour retenir la ligne.
     */
    private Specification<Prescription> buildSearchTermSpec(String q) {
        List<String> tokens = SearchTerms.tokenize(q);
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.stream()
                .map(this::matchesSearchToken)
                .reduce(Specification::and)
                .orElse(null);
    }

    private Specification<Prescription> matchesSearchToken(String token) {
        Specification<Patient> nameMatch = hasNom(token).or(hasPrenom(token));
        List<Long> matchingPatientIds = patientRepository.findAll(nameMatch).stream().map(Patient::getId).toList();
        List<Long> matchingConsultationIds = resolveConsultationIdsForPatients(matchingPatientIds);
        return PrescriptionSpecifications.matchesSearchTerm(token, matchingConsultationIds);
    }

    private List<Long> resolveConsultationIdsForPatients(List<Long> patientIds) {
        if (patientIds.isEmpty()) {
            return List.of();
        }
        List<Long> medicalRecordIds = medicalRecordRepository.findByPatientIdIn(patientIds).stream()
                .map(MedicalRecord::getId).toList();
        if (medicalRecordIds.isEmpty()) {
            return List.of();
        }
        return consultationRepository.findAll(hasMedicalRecordIdIn(medicalRecordIds))
                .stream().map(Consultation::getId).toList();
    }

    private List<Long> resolveConsultationIdsForFilter(Long patientId, Long medecinId, LocalDate dateFrom, LocalDate dateTo) {
        Specification<Consultation> spec = hasMedecinId(medecinId);

        Specification<Consultation> dateSpec = dateBetween(dateFrom, dateTo);
        if (dateSpec != null) {
            spec = (spec == null) ? dateSpec : spec.and(dateSpec);
        }

        if (patientId != null) {
            List<Long> medicalRecordIds = medicalRecordRepository.findByPatientIdIn(List.of(patientId)).stream()
                    .map(MedicalRecord::getId).toList();
            Specification<Consultation> recordSpec = hasMedicalRecordIdIn(medicalRecordIds);
            spec = (spec == null) ? recordSpec : spec.and(recordSpec);
        }

        return consultationRepository.findAll(spec).stream().map(Consultation::getId).toList();
    }

    private PrescriptionResponse enrichOne(Prescription prescription) {
        return enrichBatch(List.of(prescription)).get(0);
    }

    private List<PrescriptionResponse> enrichBatch(List<Prescription> prescriptions) {
        List<PrescriptionResponse> responses = prescriptions.stream().map(prescriptionMapper::toResponse).toList();

        Set<Long> consultationIds = prescriptions.stream().map(Prescription::getConsultationId).collect(Collectors.toSet());
        Map<Long, Consultation> consultations = consultationRepository.findAllById(consultationIds).stream()
                .collect(Collectors.toMap(Consultation::getId, c -> c));

        Set<Long> medicalRecordIds = consultations.values().stream()
                .map(Consultation::getMedicalRecordId).collect(Collectors.toSet());
        Map<Long, MedicalRecord> medicalRecords = medicalRecordRepository.findAllById(medicalRecordIds).stream()
                .collect(Collectors.toMap(MedicalRecord::getId, r -> r));

        Set<Long> patientIds = medicalRecords.values().stream()
                .map(MedicalRecord::getPatientId).collect(Collectors.toSet());
        Map<Long, Patient> patients = patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, p -> p));

        Set<Long> userIds = new HashSet<>();
        consultations.values().forEach(c -> userIds.add(c.getMedecinId()));
        prescriptions.forEach(p -> {
            if (p.getCreatedBy() != null) {
                userIds.add(p.getCreatedBy());
            }
            if (p.getUpdatedBy() != null) {
                userIds.add(p.getUpdatedBy());
            }
        });
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        for (int i = 0; i < prescriptions.size(); i++) {
            Prescription prescription = prescriptions.get(i);
            PrescriptionResponse response = responses.get(i);

            Consultation consultation = consultations.get(prescription.getConsultationId());
            if (consultation != null) {
                response.setConsultationDate(consultation.getConsultationDate());
                response.setMedecinId(consultation.getMedecinId());
                User medecin = users.get(consultation.getMedecinId());
                if (medecin != null) {
                    response.setMedecinNom(medecin.getNom());
                    response.setMedecinPrenom(medecin.getPrenom());
                }
                MedicalRecord record = medicalRecords.get(consultation.getMedicalRecordId());
                Patient patient = (record == null) ? null : patients.get(record.getPatientId());
                if (patient != null) {
                    response.setPatientId(patient.getId());
                    response.setPatientNom(patient.getNom());
                    response.setPatientPrenom(patient.getPrenom());
                }
            }

            response.setCreatedByName(resolveUserName(users, prescription.getCreatedBy()));
            response.setUpdatedByName(resolveUserName(users, prescription.getUpdatedBy()));
        }

        return responses;
    }

    private String resolveUserName(Map<Long, User> users, Long userId) {
        User user = users.get(userId);
        return user == null ? null : user.getNom() + " " + user.getPrenom();
    }

    private static Specification<Consultation> hasMedicalRecordIdIn(List<Long> medicalRecordIds) {
        if (medicalRecordIds == null) {
            return null;
        }
        if (medicalRecordIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("medicalRecordId").in(medicalRecordIds);
    }

    private static Specification<Consultation> hasMedecinId(Long medecinId) {
        if (medecinId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("medecinId"), medecinId);
    }

    private static Specification<Consultation> dateBetween(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo == null) {
            return null;
        }
        if (dateFrom != null && dateTo != null) {
            return (root, query, cb) -> cb.between(root.get("consultationDate"), dateFrom, dateTo);
        }
        if (dateFrom != null) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("consultationDate"), dateFrom);
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("consultationDate"), dateTo);
    }

    private static Specification<Patient> hasMedecinReferentId(Long medecinReferentId) {
        if (medecinReferentId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("medecinReferentId"), medecinReferentId);
    }

    private static Specification<Patient> hasNom(String nom) {
        if (!StringUtils.hasText(nom)) {
            return null;
        }
        String pattern = "%" + nom.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("nom")), pattern);
    }

    private static Specification<Patient> hasPrenom(String prenom) {
        if (!StringUtils.hasText(prenom)) {
            return null;
        }
        String pattern = "%" + prenom.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("prenom")), pattern);
    }
}
