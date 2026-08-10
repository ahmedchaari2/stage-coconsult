package tn.coconsult.medtrack.ai.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.coconsult.medtrack.ai.dto.ConsultationSummaryPage;
import tn.coconsult.medtrack.ai.dto.ConsultationSummaryResponse;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationGatewayTests {

    @Mock ConsultationServiceClient consultationServiceClient;

    @InjectMocks ConsultationGateway consultationGateway;

    @Test
    void returnsTheMostRecentConsultationsFirst() {
        ConsultationSummaryResponse oldest = consultationOn(2024, 1, 1);
        ConsultationSummaryResponse newest = consultationOn(2026, 3, 10);
        when(consultationServiceClient.search(eq(9L), anyInt(), anyInt())).thenReturn(new ConsultationSummaryPage(List.of(oldest, newest)));

        assertThat(consultationGateway.recent(9L)).containsExactly(newest, oldest);
    }

    @Test
    void keepsOnlyTheFiveMostRecent() {
        List<ConsultationSummaryResponse> sevenConsultations = List.of(
                consultationOn(2020, 1, 1), consultationOn(2021, 1, 1), consultationOn(2022, 1, 1),
                consultationOn(2023, 1, 1), consultationOn(2024, 1, 1), consultationOn(2025, 1, 1),
                consultationOn(2026, 1, 1));
        when(consultationServiceClient.search(eq(9L), anyInt(), anyInt())).thenReturn(new ConsultationSummaryPage(sevenConsultations));

        assertThat(consultationGateway.recent(9L)).hasSize(5);
    }

    @Test
    void toleratesAnEmptyHistory() {
        when(consultationServiceClient.search(eq(9L), anyInt(), anyInt())).thenReturn(new ConsultationSummaryPage(List.of()));

        assertThat(consultationGateway.recent(9L)).isEmpty();
    }

    @Test
    void toleratesANullContentInThePage() {
        when(consultationServiceClient.search(eq(9L), anyInt(), anyInt())).thenReturn(new ConsultationSummaryPage(null));

        assertThat(consultationGateway.recent(9L)).isEmpty();
    }

    private ConsultationSummaryResponse consultationOn(int year, int month, int day) {
        return new ConsultationSummaryResponse(LocalDate.of(year, month, day), "Controle", "RAS", null, null, null, null, null, null);
    }
}
