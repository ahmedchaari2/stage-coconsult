package tn.coconsult.medtrack.medicalrecord.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.accesslog.service.AccesLogService;
import tn.coconsult.medtrack.appointment.model.Appointment;
import tn.coconsult.medtrack.appointment.repository.AppointmentRepository;
import tn.coconsult.medtrack.common.dto.ConsultationSummaryResponse;
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.common.util.SortUtils;
import tn.coconsult.medtrack.medicalrecord.dto.ConsultationExistsResponse;
import tn.coconsult.medtrack.medicalrecord.dto.ConsultationFilter;
import tn.coconsult.medtrack.medicalrecord.dto.ConsultationRequest;
import tn.coconsult.medtrack.medicalrecord.dto.ConsultationResponse;
import tn.coconsult.medtrack.medicalrecord.dto.MedecinConsultationCount;
import tn.coconsult.medtrack.medicalrecord.mapper.ConsultationMapper;
import tn.coconsult.medtrack.medicalrecord.model.Consultation;
import tn.coconsult.medtrack.medicalrecord.model.MedicalRecord;
import tn.coconsult.medtrack.medicalrecord.repository.ConsultationRepository;
import tn.coconsult.medtrack.medicalrecord.repository.ConsultationSpecifications;
import tn.coconsult.medtrack.medicalrecord.repository.MedicalRecordRepository;
import tn.coconsult.medtrack.medicalrecord.client.PatientReferentClient;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ConsultationService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "consultationDate", "consultationDate",
            "motif", "motif",
            "createdAt", "createdAt");

    private final ConsultationRepository consultationRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalRecordService medicalRecordService;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final PatientReferentClient patientReferentClient;
    private final AccesLogService accesLogService;
    private final ConsultationMapper consultationMapper;

    public PageDTO<ConsultationResponse> search(Long medicalRecordId, ConsultationFilter filter, int page, int size,
                                                 String sort, String direction) {
        if (!medicalRecordRepository.existsById(medicalRecordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier médical introuvable");
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort resolvedSort = SortUtils.resolve(sort, direction, SORTABLE_FIELDS, "createdAt", Sort.Direction.DESC);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, resolvedSort);

        // Par défaut (archived non fourni ou false), le tableau exclut les consultations archivées.
        boolean archived = Boolean.TRUE.equals(filter.archived());
        ConsultationFilter effectiveFilter = new ConsultationFilter(filter.motif(), filter.medecinId(),
                filter.dateFrom(), filter.dateTo(), archived, filter.q());

        Specification<Consultation> spec = ConsultationSpecifications.hasMedicalRecordId(medicalRecordId);
        Specification<Consultation> dynamicSpec = ConsultationSpecifications.build(effectiveFilter);
        if (dynamicSpec != null) {
            spec = spec.and(dynamicSpec);
        }

        Page<Consultation> result = consultationRepository.findAll(spec, pageRequest);
        List<ConsultationResponse> content = enrichBatch(result.getContent());
        return new PageDTO<>(content, result.getNumber(), result.getSize(), result.getTotalPages(), result.getTotalElements());
    }

    public ConsultationResponse getById(Long id) {
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation introuvable"));
        MedicalRecord record = medicalRecordRepository.findById(consultation.getMedicalRecordId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier médical introuvable"));
        logAcces(TypeRessource.CONSULTATION, consultation.getId(), record.getPatientId(), TypeAction.VIEW);
        return enrichOne(consultation);
    }

    public ConsultationResponse create(Long medicalRecordId, ConsultationRequest request) {
        MedicalRecord medicalRecord = medicalRecordRepository.findById(medicalRecordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier médical introuvable"));

        Long medecinId = resolveMedecinId(request.getMedecinId());
        requireActiveMedecin(medecinId);

        Consultation consultation = new Consultation();
        consultation.setMedicalRecordId(medicalRecordId);
        consultation.setMedecinId(medecinId);
        consultation.setAppointmentId(validateAppointment(request.getAppointmentId(), medicalRecord.getPatientId()));
        applyRequestToEntity(request, consultation);
        Consultation saved = consultationRepository.save(consultation);
        logAcces(TypeRessource.CONSULTATION, saved.getId(), medicalRecord.getPatientId(), TypeAction.CREATE);
        return enrichOne(saved);
    }

    public ConsultationExistsResponse existsForAppointment(Long appointmentId) {
        return consultationRepository.findByAppointmentId(appointmentId)
                .map(consultation -> new ConsultationExistsResponse(true, consultation.getId()))
                .orElseGet(() -> new ConsultationExistsResponse(false, null));
    }

    public boolean canAccessAppointment(Long appointmentId, Long medecinId) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return false;
        }
        return medecinId != null && (medecinId.equals(appointment.getMedecinId())
                || patientReferentClient.isReferent(appointment.getPatientId(), medecinId));
    }

    private Long validateAppointment(Long appointmentId, Long patientId) {
        if (appointmentId == null) {
            return null;
        }
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rendez-vous introuvable"));
        if (!appointment.getPatientId().equals(patientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce rendez-vous ne concerne pas ce patient");
        }
        return appointmentId;
    }

    public ConsultationResponse update(Long id, ConsultationRequest request) {
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation introuvable"));
        MedicalRecord record = medicalRecordRepository.findById(consultation.getMedicalRecordId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier médical introuvable"));

        Long medecinId = resolveMedecinId(request.getMedecinId());
        if (!medecinId.equals(consultation.getMedecinId())) {
            requireActiveMedecin(medecinId);
        }
        consultation.setMedecinId(medecinId);
        consultation.setAppointmentId(validateAppointment(request.getAppointmentId(), record.getPatientId()));
        applyRequestToEntity(request, consultation);
        Consultation saved = consultationRepository.save(consultation);
        logAcces(TypeRessource.CONSULTATION, saved.getId(), record.getPatientId(), TypeAction.UPDATE);
        return enrichOne(saved);
    }

    // Soft delete (jamais de suppression physique en logiciel médical), idempotent.
    public void delete(Long id) {
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation introuvable"));
        if (!consultation.isArchived()) {
            MedicalRecord record = medicalRecordRepository.findById(consultation.getMedicalRecordId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier médical introuvable"));
            consultation.setArchived(true);
            consultation.setArchivedAt(LocalDateTime.now());
            consultationRepository.save(consultation);
            logAcces(TypeRessource.CONSULTATION, consultation.getId(), record.getPatientId(), TypeAction.ARCHIVE);
        }
    }

    // Réactive une consultation archivée, idempotent.
    public ConsultationResponse restore(Long id) {
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation introuvable"));

        if (consultation.isArchived()) {
            MedicalRecord record = medicalRecordRepository.findById(consultation.getMedicalRecordId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier médical introuvable"));
            consultation.setArchived(false);
            consultation.setArchivedAt(null);
            consultation = consultationRepository.save(consultation);
            logAcces(TypeRessource.CONSULTATION, consultation.getId(), record.getPatientId(), TypeAction.RESTORE);
        }
        return enrichOne(consultation);
    }

    public boolean isMedecinReferentForConsultation(Long consultationId, Long medecinId) {
        Consultation consultation = consultationRepository.findById(consultationId).orElse(null);
        if (consultation == null) {
            return false;
        }
        return medicalRecordService.isMedecinReferentForRecord(consultation.getMedicalRecordId(), medecinId);
    }

    public ConsultationSummaryResponse getSummary(Long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation introuvable"));
        MedicalRecord record = medicalRecordRepository.findById(consultation.getMedicalRecordId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier médical introuvable"));
        return new ConsultationSummaryResponse(record.getId(), record.getPatientId(), consultation.getMedecinId());
    }

    public List<MedecinConsultationCount> countByMedecin(LocalDate dateFrom, LocalDate dateTo) {
        Specification<Consultation> spec = ConsultationSpecifications.hasArchived(false);
        Specification<Consultation> dateSpec = ConsultationSpecifications.dateBetween(dateFrom, dateTo);
        if (dateSpec != null) {
            spec = spec.and(dateSpec);
        }
        List<MedecinConsultationCount> counts = consultationRepository.findAll(spec).stream()
                .collect(Collectors.groupingBy(Consultation::getMedecinId, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new MedecinConsultationCount(entry.getKey(), entry.getValue()))
                .toList();
        User currentUser = currentUser();
        return currentUser.getRole() == Role.MEDECIN
                ? counts.stream().filter(count -> currentUser.getId().equals(count.medecinId())).toList()
                : counts;
    }

    public List<Long> patientIdsWithRecord() {
        User currentUser = currentUser();
        Set<Long> allowedPatientIds = currentUser.getRole() == Role.MEDECIN
                ? patientRepository.findByMedecinReferentId(currentUser.getId()).stream()
                        .map(Patient::getId)
                        .collect(Collectors.toSet())
                : null;
        return medicalRecordRepository.findAll().stream()
                .map(MedicalRecord::getPatientId)
                .filter(patientId -> allowedPatientIds == null || allowedPatientIds.contains(patientId))
                .toList();
    }

    private Long resolveMedecinId(Long requestedMedecinId) {
        User currentUser = currentUser();

        if (currentUser.getRole() == Role.MEDECIN) {
            return currentUser.getId();
        }
        if (requestedMedecinId == null) {
            throw new IllegalArgumentException("medecinId est obligatoire pour un ADMIN");
        }
        return requestedMedecinId;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (User) auth.getPrincipal();
    }

    private void requireActiveMedecin(Long medecinId) {
        User medecin = userRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable"));
        if (medecin.getRole() != Role.MEDECIN) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "L'utilisateur sélectionné n'est pas un médecin");
        }
        if (Boolean.FALSE.equals(medecin.getActif())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Le médecin est inactif");
        }
    }

    private void logAcces(TypeRessource type, Long ressourceId, Long patientId, TypeAction action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();
        accesLogService.enregistrerAcces(currentUser.getId(), type, ressourceId, patientId, action);
    }

    private void applyRequestToEntity(ConsultationRequest request, Consultation consultation) {
        consultation.setConsultationDate(request.getConsultationDate());
        consultation.setMotif(request.getMotif());
        consultation.setDiagnostic(request.getDiagnostic());
        consultation.setNotes(request.getNotes());
        consultation.setTensionArterielleSystolique(request.getTensionArterielleSystolique());
        consultation.setTensionArterielleDiastolique(request.getTensionArterielleDiastolique());
        consultation.setPoids(request.getPoids());
        consultation.setTemperature(request.getTemperature());
        consultation.setPouls(request.getPouls());
    }

    private ConsultationResponse enrichOne(Consultation consultation) {
        return enrichBatch(List.of(consultation)).get(0);
    }

    private List<ConsultationResponse> enrichBatch(List<Consultation> consultations) {
        List<ConsultationResponse> responses = consultations.stream().map(consultationMapper::toResponse).toList();

        Set<Long> userIds = consultations.stream()
                .flatMap(c -> Stream.of(c.getCreatedBy(), c.getUpdatedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        for (int i = 0; i < consultations.size(); i++) {
            Consultation consultation = consultations.get(i);
            ConsultationResponse response = responses.get(i);
            response.setCreatedByName(resolveUserName(users, consultation.getCreatedBy()));
            response.setUpdatedByName(resolveUserName(users, consultation.getUpdatedBy()));
        }

        return responses;
    }

    private String resolveUserName(Map<Long, User> users, Long userId) {
        User user = users.get(userId);
        return user == null ? null : user.getNom() + " " + user.getPrenom();
    }
}
