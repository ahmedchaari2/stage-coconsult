package tn.coconsult.medtrack.medicalrecord.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.coconsult.medtrack.accesslog.dto.AccesLogFilter;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.medicalrecord.dto.AccesLogResponse;
import tn.coconsult.medtrack.medicalrecord.service.AccesLogQueryService;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
public class AccesLogController {

    private final AccesLogQueryService accesLogQueryService;

    @GetMapping("/api/acces-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageDTO<AccesLogResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) TypeRessource typeRessource,
            @RequestParam(required = false) TypeAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        AccesLogFilter filter = new AccesLogFilter(utilisateurId, patientId, typeRessource, action, dateFrom, dateTo, q);
        return ResponseEntity.ok(accesLogQueryService.search(filter, page, size, sort, direction));
    }
}
