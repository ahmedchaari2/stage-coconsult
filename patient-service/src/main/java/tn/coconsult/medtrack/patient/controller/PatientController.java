package tn.coconsult.medtrack.patient.controller;

import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.common.dto.ReferentCheckResponse;
import tn.coconsult.medtrack.patient.dto.PatientFilter;
import tn.coconsult.medtrack.patient.dto.PatientRequest;
import tn.coconsult.medtrack.patient.dto.PatientResponse;
import tn.coconsult.medtrack.patient.service.PatientService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PostMapping({"", "/"})
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientRequest request) {
        PatientResponse response = patientService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping({"", "/"})
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<PageDTO<PatientResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nom,
            @RequestParam(required = false) String prenom,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Long medecinReferentId,
            @RequestParam(required = false) String telephone,
            @RequestParam(required = false) String numeroDossier,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateNaissanceFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateNaissanceTo,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean sansNumeroDossier,
            @RequestParam(required = false) Boolean sansCin,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        PatientFilter filter = new PatientFilter(nom, prenom, email, medecinReferentId, telephone, numeroDossier,
                dateNaissanceFrom, dateNaissanceTo, archived, q, sansNumeroDossier, sansCin);
        return ResponseEntity.ok(patientService.search(filter, page, size, sort, direction));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @patientService.isMedecinReferent(#id, authentication.principal.id))")
    public ResponseEntity<PatientResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(patientService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @patientService.isMedecinReferent(#id, authentication.principal.id))")
    public ResponseEntity<PatientResponse> update(@PathVariable Long id, @Valid @RequestBody PatientRequest request) {
        return ResponseEntity.ok(patientService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @patientService.isMedecinReferent(#id, authentication.principal.id))")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patientService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PatientResponse> restore(@PathVariable Long id) {
        return ResponseEntity.ok(patientService.restore(id));
    }

    // Endpoint interne, appelé service-à-service (ex. medicalrecord-service via Feign). Le segment
    // /internal le fait rejeter en 404 par la Gateway : joignable seulement depuis le réseau interne.
    @GetMapping("/internal/{id}/is-referent")
    public ResponseEntity<ReferentCheckResponse> isReferent(@PathVariable Long id, @RequestParam Long medecinId) {
        return ResponseEntity.ok(new ReferentCheckResponse(patientService.isMedecinReferent(id, medecinId)));
    }
}
