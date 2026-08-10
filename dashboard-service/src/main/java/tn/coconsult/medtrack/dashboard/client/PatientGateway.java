package tn.coconsult.medtrack.dashboard.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.dashboard.dto.PatientPage;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class PatientGateway {

    private static final int PAGE_SIZE = 100;

    private final PatientClient patientClient;

    @CircuitBreaker(name = "patientService", fallbackMethod = "unavailable")
    public PatientPage search(Long medecinReferentId, Boolean archived) {
        PatientPage firstPage = patientClient.search(0, PAGE_SIZE, medecinReferentId, archived);
        long total = firstPage.totalElements();
        var patients = new ArrayList<>(firstPage.safeContent());
        for (int page = 1; (long) page * PAGE_SIZE < total; page++) {
            patients.addAll(patientClient.search(page, PAGE_SIZE, medecinReferentId, archived).safeContent());
        }
        return new PatientPage(patients, total);
    }

    private PatientPage unavailable(Long medecinReferentId, Boolean archived, Throwable t) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "patient-service indisponible");
    }
}
