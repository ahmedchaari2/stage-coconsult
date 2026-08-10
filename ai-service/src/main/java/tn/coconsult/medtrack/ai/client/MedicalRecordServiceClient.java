package tn.coconsult.medtrack.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tn.coconsult.medtrack.ai.dto.MedicalRecordSummaryResponse;

@FeignClient(name = "medicalrecord-service", contextId = "medicalRecordServiceClient")
public interface MedicalRecordServiceClient {

    @GetMapping("/api/patients/{patientId}/medical-record")
    MedicalRecordSummaryResponse getByPatientId(@PathVariable("patientId") Long patientId);
}
