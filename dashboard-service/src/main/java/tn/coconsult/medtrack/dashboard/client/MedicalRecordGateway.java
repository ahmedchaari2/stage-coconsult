package tn.coconsult.medtrack.dashboard.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.dashboard.dto.MedecinConsultationCountDto;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicalRecordGateway {

    private final MedicalRecordClient medicalRecordClient;

    @CircuitBreaker(name = "medicalRecordService", fallbackMethod = "countUnavailable")
    public List<MedecinConsultationCountDto> consultationCountByMedecin(String dateFrom, String dateTo) {
        return medicalRecordClient.consultationCountByMedecin(dateFrom, dateTo);
    }

    @CircuitBreaker(name = "medicalRecordService", fallbackMethod = "idsUnavailable")
    public List<Long> patientIdsWithRecord() {
        return medicalRecordClient.patientIdsWithRecord();
    }

    private List<MedecinConsultationCountDto> countUnavailable(String dateFrom, String dateTo, Throwable t) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "medicalrecord-service indisponible");
    }

    private List<Long> idsUnavailable(Throwable t) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "medicalrecord-service indisponible");
    }
}
