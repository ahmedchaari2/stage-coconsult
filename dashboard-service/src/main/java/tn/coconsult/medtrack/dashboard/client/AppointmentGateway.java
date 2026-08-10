package tn.coconsult.medtrack.dashboard.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.dashboard.dto.AppointmentPage;

import java.util.ArrayList;
import java.util.List;

/**
 * Enveloppe protégée d'AppointmentClient : @CircuitBreaker passe par un proxy Spring AOP, un
 * self-appel interne ne serait pas intercepté d'où ce bean à part (même raison que les gateways
 * Feign des autres services). Le fallback lève 503, que DashboardService rattrape pour marquer
 * la section indisponible sans faire échouer tout le dashboard.
 */
@Service
@RequiredArgsConstructor
public class AppointmentGateway {

    private static final int PAGE_SIZE = 100;

    private final AppointmentClient appointmentClient;

    @CircuitBreaker(name = "appointmentService", fallbackMethod = "unavailable")
    public AppointmentPage search(Long medecinId, String statut, String dateFrom, String dateTo) {
        AppointmentPage firstPage = appointmentClient.search(0, PAGE_SIZE, medecinId, statut, dateFrom, dateTo);
        long total = firstPage.totalElements();
        List<tn.coconsult.medtrack.dashboard.dto.AppointmentDto> appointments = new ArrayList<>(firstPage.safeContent());

        for (int page = 1; (long) page * PAGE_SIZE < total; page++) {
            appointments.addAll(appointmentClient.search(page, PAGE_SIZE, medecinId, statut, dateFrom, dateTo).safeContent());
        }

        return new AppointmentPage(appointments, total);
    }

    private AppointmentPage unavailable(Long medecinId, String statut, String dateFrom, String dateTo, Throwable t) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "appointment-service indisponible");
    }
}
