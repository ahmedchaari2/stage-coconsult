package tn.coconsult.medtrack.medicalrecord.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.coconsult.medtrack.medicalrecord.dto.MedicalRecordRequest;
import tn.coconsult.medtrack.medicalrecord.dto.MedicalRecordResponse;
import tn.coconsult.medtrack.medicalrecord.service.MedicalRecordService;

@RestController
@RequestMapping("/api/patients/{patientId}/medical-record")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    @GetMapping({"", "/"})
    // Verif referent faite dans MedicalRecordService (appel Feign) : plus de bean patientService local pour le faire en SpEL.
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<MedicalRecordResponse> getByPatientId(@PathVariable Long patientId) {
        return ResponseEntity.ok(medicalRecordService.getByPatientId(patientId));
    }

    @PostMapping({"", "/"})
    // Verif referent faite dans MedicalRecordService (appel Feign) : plus de bean patientService local pour le faire en SpEL.
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<MedicalRecordResponse> create(@PathVariable Long patientId,
                                                          @Valid @RequestBody MedicalRecordRequest request) {
        MedicalRecordResponse response = medicalRecordService.create(patientId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping({"", "/"})
    // Verif referent faite dans MedicalRecordService (appel Feign) : plus de bean patientService local pour le faire en SpEL.
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<MedicalRecordResponse> update(@PathVariable Long patientId,
                                                          @Valid @RequestBody MedicalRecordRequest request) {
        return ResponseEntity.ok(medicalRecordService.update(patientId, request));
    }
}
