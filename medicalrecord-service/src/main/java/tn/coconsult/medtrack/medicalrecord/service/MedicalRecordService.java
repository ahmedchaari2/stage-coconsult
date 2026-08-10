package tn.coconsult.medtrack.medicalrecord.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.accesslog.service.AccesLogService;
import tn.coconsult.medtrack.medicalrecord.client.PatientReferentClient;
import tn.coconsult.medtrack.medicalrecord.dto.MedicalRecordRequest;
import tn.coconsult.medtrack.medicalrecord.dto.MedicalRecordResponse;
import tn.coconsult.medtrack.medicalrecord.mapper.MedicalRecordMapper;
import tn.coconsult.medtrack.medicalrecord.model.MedicalRecord;
import tn.coconsult.medtrack.medicalrecord.repository.MedicalRecordRepository;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepository patientRepository;
    private final PatientReferentClient patientReferentClient;
    private final UserRepository userRepository;
    private final MedicalRecordMapper medicalRecordMapper;
    private final AccesLogService accesLogService;

    public MedicalRecordResponse getByPatientId(Long patientId) {
        requireAccess(patientId);
        MedicalRecord record = findByPatientIdOrThrow(patientId);
        logAcces(TypeRessource.DOSSIER_MEDICAL, record.getId(), patientId, TypeAction.VIEW);
        return enrich(record);
    }

    public MedicalRecordResponse create(Long patientId, MedicalRecordRequest request) {
        requireAccess(patientId);
        if (!patientRepository.existsById(patientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient introuvable");
        }
        if (medicalRecordRepository.findByPatientId(patientId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un dossier médical existe déjà pour ce patient");
        }

        MedicalRecord record = new MedicalRecord();
        record.setPatientId(patientId);
        applyRequestToEntity(request, record);
        MedicalRecord saved = medicalRecordRepository.save(record);
        logAcces(TypeRessource.DOSSIER_MEDICAL, saved.getId(), patientId, TypeAction.CREATE);
        return enrich(saved);
    }

    public MedicalRecordResponse update(Long patientId, MedicalRecordRequest request) {
        requireAccess(patientId);
        MedicalRecord record = findByPatientIdOrThrow(patientId);
        applyRequestToEntity(request, record);
        MedicalRecord saved = medicalRecordRepository.save(record);
        logAcces(TypeRessource.DOSSIER_MEDICAL, saved.getId(), patientId, TypeAction.UPDATE);
        return enrich(saved);
    }

    /**
     * Vérifie si un médecin est le référent du patient propriétaire de ce dossier médical.
     * Utilisé par ConsultationService (consultation -> dossier médical -> patient), qui reste
     * un bean local ici : seul l'appel vers patient-service passe par le réseau (Feign).
     */
    public boolean isMedecinReferentForRecord(Long medicalRecordId, Long medecinId) {
        MedicalRecord record = medicalRecordRepository.findById(medicalRecordId).orElse(null);
        if (record == null) {
            return false;
        }
        return patientReferentClient.isReferent(record.getPatientId(), medecinId);
    }

    /**
     * Contrôle d'accès patientId, en Java plutôt qu'en @PreAuthorize SpEL : patient-service
     * (et son bean isMedecinReferent) ne vit plus dans ce service, l'appel se fait via Feign.
     */
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

    private MedicalRecord findByPatientIdOrThrow(Long patientId) {
        return medicalRecordRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun dossier médical pour ce patient"));
    }

    private void applyRequestToEntity(MedicalRecordRequest request, MedicalRecord record) {
        record.setGroupeSanguin(request.getGroupeSanguin());
        record.setAllergies(request.getAllergies());
        record.setAntecedents(request.getAntecedents());
        record.setTraitementsChroniques(request.getTraitementsChroniques());
        record.setAntecedentsFamiliaux(request.getAntecedentsFamiliaux());
        record.setVaccinations(request.getVaccinations());
    }

    private MedicalRecordResponse enrich(MedicalRecord record) {
        MedicalRecordResponse response = medicalRecordMapper.toResponse(record);
        response.setCreatedByName(resolveUserName(record.getCreatedBy()));
        response.setUpdatedByName(resolveUserName(record.getUpdatedBy()));
        return response;
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(u -> u.getNom() + " " + u.getPrenom())
                .orElse(null);
    }
}
