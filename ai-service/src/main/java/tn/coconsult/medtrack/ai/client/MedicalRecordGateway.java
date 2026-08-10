package tn.coconsult.medtrack.ai.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.ai.dto.MedicalRecordSummaryResponse;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MedicalRecordGateway {

    private final MedicalRecordServiceClient medicalRecordServiceClient;

    // A missing record is a valid state, so treat 404 as an empty context rather than an error.
    @CircuitBreaker(name = "medicalRecordService", fallbackMethod = "getByPatientIdFallback")
    public Optional<MedicalRecordSummaryResponse> getByPatientId(Long patientId) {
        try {
            return Optional.of(medicalRecordServiceClient.getByPatientId(patientId));
        } catch (FeignException.NotFound notFound) {
            return Optional.empty();
        }
    }

    private Optional<MedicalRecordSummaryResponse> getByPatientIdFallback(Long patientId, Throwable throwable) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "medicalrecord-service indisponible, impossible de recuperer le dossier medical");
    }
}
