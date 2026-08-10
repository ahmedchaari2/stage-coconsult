package tn.coconsult.medtrack.medicalrecord.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.coconsult.medtrack.common.dto.ConsultationSummaryResponse;
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.medicalrecord.dto.ConsultationExistsResponse;
import tn.coconsult.medtrack.medicalrecord.dto.ConsultationFilter;
import tn.coconsult.medtrack.medicalrecord.dto.ConsultationRequest;
import tn.coconsult.medtrack.medicalrecord.dto.ConsultationResponse;
import tn.coconsult.medtrack.medicalrecord.dto.MedecinConsultationCount;
import tn.coconsult.medtrack.medicalrecord.service.ConsultationService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

    @GetMapping("/api/medical-records/{id}/consultations")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @medicalRecordService.isMedecinReferentForRecord(#id, authentication.principal.id))")
    public ResponseEntity<PageDTO<ConsultationResponse>> search(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String motif,
            @RequestParam(required = false) Long medecinId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        ConsultationFilter filter = new ConsultationFilter(motif, medecinId, dateFrom, dateTo, archived, q);
        return ResponseEntity.ok(consultationService.search(id, filter, page, size, sort, direction));
    }

    @PostMapping("/api/medical-records/{id}/consultations")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @medicalRecordService.isMedecinReferentForRecord(#id, authentication.principal.id))")
    public ResponseEntity<ConsultationResponse> create(@PathVariable Long id,
                                                         @Valid @RequestBody ConsultationRequest request) {
        ConsultationResponse response = consultationService.create(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/consultations/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @consultationService.isMedecinReferentForConsultation(#id, authentication.principal.id))")
    public ResponseEntity<ConsultationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(consultationService.getById(id));
    }

    @PutMapping("/api/consultations/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @consultationService.isMedecinReferentForConsultation(#id, authentication.principal.id))")
    public ResponseEntity<ConsultationResponse> update(@PathVariable Long id,
                                                         @Valid @RequestBody ConsultationRequest request) {
        return ResponseEntity.ok(consultationService.update(id, request));
    }

    @DeleteMapping("/api/consultations/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @consultationService.isMedecinReferentForConsultation(#id, authentication.principal.id))")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        consultationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/consultations/{id}/restore")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @consultationService.isMedecinReferentForConsultation(#id, authentication.principal.id))")
    public ResponseEntity<ConsultationResponse> restore(@PathVariable Long id) {
        return ResponseEntity.ok(consultationService.restore(id));
    }

    // Endpoint interne (prescription-service via Feign), pas de rôle précis requis, comme is-referent.
    @GetMapping("/api/consultations/{id}/summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<ConsultationSummaryResponse> summary(@PathVariable Long id) {
        return ResponseEntity.ok(consultationService.getSummary(id));
    }

    @GetMapping("/api/consultations/by-appointment/{appointmentId}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @consultationService.canAccessAppointment(#appointmentId, authentication.principal.id))")
    public ResponseEntity<ConsultationExistsResponse> existsForAppointment(@PathVariable Long appointmentId) {
        return ResponseEntity.ok(consultationService.existsForAppointment(appointmentId));
    }

    @GetMapping("/api/consultations/internal/count-by-medecin")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<List<MedecinConsultationCount>> countByMedecin(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(consultationService.countByMedecin(dateFrom, dateTo));
    }

    @GetMapping("/api/medical-records/internal/patient-ids")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<List<Long>> patientIdsWithRecord() {
        return ResponseEntity.ok(consultationService.patientIdsWithRecord());
    }
}
