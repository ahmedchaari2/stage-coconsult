package tn.coconsult.medtrack.prescription.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.prescription.dto.PrescriptionFilter;
import tn.coconsult.medtrack.prescription.dto.PrescriptionRequest;
import tn.coconsult.medtrack.prescription.dto.PrescriptionResponse;
import tn.coconsult.medtrack.prescription.service.PrescriptionService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    // Vérification médecin référent déléguée à PrescriptionService (chaîne Feign consultation -> patient) : @consultationService n'est pas un bean local ici.
    @GetMapping("/api/consultations/{consultationId}/prescriptions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<List<PrescriptionResponse>> listForConsultation(
            @PathVariable Long consultationId,
            @RequestParam(required = false) Boolean archived) {
        return ResponseEntity.ok(prescriptionService.listForConsultation(consultationId, archived));
    }

    @PostMapping("/api/consultations/{consultationId}/prescriptions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<PrescriptionResponse> create(@PathVariable Long consultationId,
                                                         @RequestParam(defaultValue = "false") boolean renewal,
                                                         @Valid @RequestBody PrescriptionRequest request) {
        PrescriptionResponse response = prescriptionService.create(consultationId, request, renewal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(value = "/api/consultations/{consultationId}/ordonnance", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<byte[]> ordonnance(@PathVariable Long consultationId) {
        byte[] pdf = prescriptionService.generateOrdonnance(consultationId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"ordonnance-" + consultationId + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/api/prescriptions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<PageDTO<PrescriptionResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String medicament,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long medecinId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String statutCalcule,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        PrescriptionFilter filter = new PrescriptionFilter(medicament, patientId, medecinId, dateFrom, dateTo, archived, q, statutCalcule);
        return ResponseEntity.ok(prescriptionService.search(filter, page, size, sort, direction));
    }

    @GetMapping("/api/prescriptions/internal/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> count(
            @RequestParam(required = false) Long medecinId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean archived) {
        PrescriptionFilter filter = new PrescriptionFilter(null, null, medecinId, dateFrom, dateTo, archived, null, null);
        return ResponseEntity.ok(prescriptionService.count(filter));
    }

    @GetMapping("/api/prescriptions/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @prescriptionService.isMedecinReferentForPrescription(#id, authentication.principal.id))")
    public ResponseEntity<PrescriptionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(prescriptionService.getById(id));
    }

    @PutMapping("/api/prescriptions/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @prescriptionService.isMedecinReferentForPrescription(#id, authentication.principal.id))")
    public ResponseEntity<PrescriptionResponse> update(@PathVariable Long id, @Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.ok(prescriptionService.update(id, request));
    }

    @DeleteMapping("/api/prescriptions/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @prescriptionService.isMedecinReferentForPrescription(#id, authentication.principal.id))")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        prescriptionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/prescriptions/{id}/restore")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @prescriptionService.isMedecinReferentForPrescription(#id, authentication.principal.id))")
    public ResponseEntity<PrescriptionResponse> restore(@PathVariable Long id) {
        return ResponseEntity.ok(prescriptionService.restore(id));
    }
}
