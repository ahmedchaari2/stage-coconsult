package tn.coconsult.medtrack.ai.client;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.ai.dto.PatientSummaryResponse;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientGatewayTests {

    @Mock PatientServiceClient patientServiceClient;

    @InjectMocks PatientGateway patientGateway;

    @Test
    void returnsThePatientWhenFound() {
        PatientSummaryResponse patient = new PatientSummaryResponse(LocalDate.of(1990, 1, 1), "HOMME");
        when(patientServiceClient.getById(1L)).thenReturn(patient);

        assertThat(patientGateway.getById(1L)).isSameAs(patient);
    }

    @Test
    void turnsAMissingPatientIntoA404NotA503() {
        when(patientServiceClient.getById(99L)).thenThrow(notFound());

        assertThatThrownBy(() -> patientGateway.getById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
    }

    private static FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/patients/99", Map.of(), Request.Body.empty(), null);
        return new FeignException.NotFound("Not Found", request, null, Map.of());
    }
}
