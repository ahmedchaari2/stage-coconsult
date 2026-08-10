package tn.coconsult.medtrack.ai.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.ai.dto.PatientSummaryResponse;

@Service
@RequiredArgsConstructor
public class PatientGateway {

    private final PatientServiceClient patientServiceClient;

    @CircuitBreaker(name = "patientService", fallbackMethod = "getByIdFallback")
    public PatientSummaryResponse getById(Long patientId) {
        try {
            return patientServiceClient.getById(patientId);
        } catch (FeignException.NotFound notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient introuvable");
        }
    }

    private PatientSummaryResponse getByIdFallback(Long patientId, Throwable throwable) {
        if (throwable instanceof ResponseStatusException statusException) {
            throw statusException;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "patient-service indisponible, impossible de recuperer le patient");
    }
}
