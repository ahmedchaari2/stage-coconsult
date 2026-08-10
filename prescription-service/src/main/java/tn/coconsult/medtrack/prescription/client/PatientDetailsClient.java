package tn.coconsult.medtrack.prescription.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.prescription.dto.PatientDetailsResponse;

@Service
@RequiredArgsConstructor
public class PatientDetailsClient {

    private final PatientServiceClient patientServiceClient;

    @CircuitBreaker(name = "patientService", fallbackMethod = "getByIdFallback")
    public PatientDetailsResponse getById(Long patientId) {
        try {
            return patientServiceClient.getById(patientId);
        } catch (FeignException.NotFound notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient introuvable");
        }
    }

    private PatientDetailsResponse getByIdFallback(Long patientId, Throwable throwable) {
        // Meme relais que ConsultationSummaryClient : un 404 metier ne doit pas ressortir en 503.
        if (throwable instanceof ResponseStatusException statusException) {
            throw statusException;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "patient-service indisponible, impossible de recuperer le patient");
    }
}
