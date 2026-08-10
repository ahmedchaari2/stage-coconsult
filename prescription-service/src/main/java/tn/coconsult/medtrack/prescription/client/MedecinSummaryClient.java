package tn.coconsult.medtrack.prescription.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.common.dto.MedecinSummaryResponse;

@Service
@RequiredArgsConstructor
public class MedecinSummaryClient {

    private final UserServiceClient userServiceClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "getSummaryFallback")
    public MedecinSummaryResponse getSummary(Long medecinId) {
        try {
            return userServiceClient.getMedecinSummary(medecinId);
        } catch (FeignException.NotFound notFound) {
            // Medecin absent : vraie 404, pas une panne (ignoree par le circuit breaker).
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medecin introuvable");
        }
    }

    private MedecinSummaryResponse getSummaryFallback(Long medecinId, Throwable throwable) {
        // Meme relais que ConsultationSummaryClient : un 404 metier ne doit pas ressortir en 503.
        if (throwable instanceof ResponseStatusException statusException) {
            throw statusException;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "user-service indisponible, impossible de recuperer le medecin");
    }
}
