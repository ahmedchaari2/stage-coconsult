package tn.coconsult.medtrack.ai.client;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.coconsult.medtrack.ai.dto.MedicalRecordSummaryResponse;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRecordGatewayTests {

    @Mock MedicalRecordServiceClient medicalRecordServiceClient;

    @InjectMocks MedicalRecordGateway medicalRecordGateway;

    @Test
    void returnsTheRecordWhenOneExists() {
        MedicalRecordSummaryResponse record = new MedicalRecordSummaryResponse(9L, "A_POSITIF", "Aspirine", null, null, null, null);
        when(medicalRecordServiceClient.getByPatientId(1L)).thenReturn(record);

        assertThat(medicalRecordGateway.getByPatientId(1L)).contains(record);
    }

    // Absence de dossier medical = etat legitime pour un patient recemment cree, pas une panne :
    // ne doit jamais remonter comme une erreur (voir le commentaire sur MedicalRecordGateway).
    @Test
    void treatsAMissingRecordAsAnEmptyOptionalNotAnError() {
        when(medicalRecordServiceClient.getByPatientId(2L)).thenThrow(notFound());

        assertThat(medicalRecordGateway.getByPatientId(2L)).isEmpty();
    }

    private static FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/patients/2/medical-record", Map.of(), Request.Body.empty(), null);
        return new FeignException.NotFound("Not Found", request, null, Map.of());
    }
}
