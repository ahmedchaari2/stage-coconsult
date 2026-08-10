package tn.coconsult.medtrack.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tn.coconsult.medtrack.dashboard.dto.MedecinConsultationCountDto;

import java.util.List;

@FeignClient(name = "medicalrecord-service", contextId = "medicalRecordClient")
public interface MedicalRecordClient {

    @GetMapping("/api/consultations/internal/count-by-medecin")
    List<MedecinConsultationCountDto> consultationCountByMedecin(
            @RequestParam String dateFrom,
            @RequestParam String dateTo);

    @GetMapping("/api/medical-records/internal/patient-ids")
    List<Long> patientIdsWithRecord();
}
