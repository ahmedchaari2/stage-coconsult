package tn.coconsult.medtrack.medicalrecord.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PatientReferentClient {

    private final PatientServiceClient patientServiceClient;

    @CircuitBreaker(name = "patientService", fallbackMethod = "isReferentFallback")
    public boolean isReferent(Long patientId, Long medecinId) {
        return patientServiceClient.isReferent(patientId, medecinId).referent();
    }

    private boolean isReferentFallback(Long patientId, Long medecinId, Throwable throwable) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "patient-service indisponible, impossible de verifier le medecin referent");
    }
}
