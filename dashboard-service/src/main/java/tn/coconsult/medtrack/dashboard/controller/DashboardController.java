package tn.coconsult.medtrack.dashboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.coconsult.medtrack.dashboard.dto.AdminDashboardResponse;
import tn.coconsult.medtrack.dashboard.dto.AujourdhuiResponse;
import tn.coconsult.medtrack.dashboard.dto.MedecinDashboardResponse;
import tn.coconsult.medtrack.dashboard.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/medecin")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<MedecinDashboardResponse> medecin() {
        return ResponseEntity.ok(dashboardService.medecinDashboard());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminDashboardResponse> admin(@RequestParam(defaultValue = "mois") String periode) {
        return ResponseEntity.ok(dashboardService.adminDashboard(periode));
    }

    @GetMapping("/aujourdhui")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<AujourdhuiResponse> aujourdhui() {
        return ResponseEntity.ok(dashboardService.aujourdhui());
    }
}
