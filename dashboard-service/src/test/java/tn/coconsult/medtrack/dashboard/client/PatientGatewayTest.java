package tn.coconsult.medtrack.dashboard.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.coconsult.medtrack.dashboard.dto.PatientDto;
import tn.coconsult.medtrack.dashboard.dto.PatientPage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientGatewayTest {

    @Mock
    private PatientClient patientClient;

    @Test
    void searchCollectsEveryPageBeforeComputingDashboardStatistics() {
        PatientDto first = patient(1L);
        PatientDto second = patient(101L);
        when(patientClient.search(0, 100, null, false)).thenReturn(new PatientPage(List.of(first), 101));
        when(patientClient.search(1, 100, null, false)).thenReturn(new PatientPage(List.of(second), 101));

        PatientPage result = new PatientGateway(patientClient).search(null, false);

        assertThat(result.totalElements()).isEqualTo(101);
        assertThat(result.safeContent()).containsExactly(first, second);
        verify(patientClient).search(0, 100, null, false);
        verify(patientClient).search(1, 100, null, false);
    }

    private PatientDto patient(Long id) {
        return new PatientDto(id, "Nom", "Prénom", LocalDate.of(1990, 1, 1), "FEMME",
                "12345678", "PAT-2026-00001", 2L, false, LocalDateTime.of(2026, 7, 1, 10, 0));
    }
}
