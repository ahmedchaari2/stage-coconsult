package tn.coconsult.medtrack.appointment.controller;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.appointment.dto.AppointmentFilter;
import tn.coconsult.medtrack.appointment.dto.CreateAppointmentRequest;
import tn.coconsult.medtrack.appointment.dto.UpdateAppointmentRequest;
import tn.coconsult.medtrack.appointment.dto.AppointmentResponse;
import tn.coconsult.medtrack.appointment.model.StatutRendezVous;
import tn.coconsult.medtrack.appointment.service.AppointmentService;
import tn.coconsult.medtrack.common.dto.PageDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    // Verification medecin referent deplacee dans AppointmentService (appel Feign vers
    @PostMapping({"", "/"})
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentResponse response = appointmentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping({"", "/"})
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<PageDTO<AppointmentResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long medecinId,
            @RequestParam(required = false) StatutRendezVous statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        AppointmentFilter filter = new AppointmentFilter(patientId, medecinId, statut, dateFrom, dateTo, q);
        return ResponseEntity.ok(appointmentService.search(filter, page, size, sort, direction));
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<List<AppointmentResponse>> calendar(@RequestParam String month) {
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Format de mois invalide, attendu YYYY-MM (ex. 2026-07)");
        }
        return ResponseEntity.ok(appointmentService.calendar(yearMonth));
    }

    /**
     * Créneaux libres d'un médecin pour une journée, au format HH:mm : de quoi proposer une liste
     * au lieu de laisser saisir une heure libre qui se ferait refuser en 409. Durée de créneau et
     * heures d'ouverture viennent du config-server (voir AppointmentService).
     *
     * @param medecinId obligatoire pour un ADMIN, ignoré pour un MEDECIN (forcé à lui-même,
     *                  même règle que la création — voir AppointmentService.resolveMedecinId)
     * @param date      au format YYYY-MM-DD (ex. 2026-07-23)
     */
    @GetMapping("/disponibilites")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<List<String>> disponibilites(
            @RequestParam(required = false) Long medecinId,
            @RequestParam String date) {
        LocalDate jour;
        try {
            jour = LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Format de date invalide, attendu YYYY-MM-DD (ex. 2026-07-23)");
        }
        return ResponseEntity.ok(appointmentService.disponibilites(medecinId, jour));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @appointmentService.isMedecinReferentForAppointment(#id, authentication.principal.id))")
    public ResponseEntity<AppointmentResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @appointmentService.isMedecinReferentForAppointment(#id, authentication.principal.id))")
    public ResponseEntity<AppointmentResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateAppointmentRequest request) {
        return ResponseEntity.ok(appointmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MEDECIN') and @appointmentService.isMedecinReferentForAppointment(#id, authentication.principal.id))")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        appointmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
