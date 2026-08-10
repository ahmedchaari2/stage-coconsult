package tn.coconsult.medtrack.ai.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.coconsult.medtrack.ai.dto.AppointmentSummaryPage;
import tn.coconsult.medtrack.ai.dto.AppointmentSummaryResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentGatewayTests {

    @Mock AppointmentServiceClient appointmentServiceClient;

    @InjectMocks AppointmentGateway appointmentGateway;

    @Test
    void returnsTheMostRecentAppointmentsFirst() {
        AppointmentSummaryResponse oldest = appointmentAt(2024, 1, 1);
        AppointmentSummaryResponse middle = appointmentAt(2025, 6, 15);
        AppointmentSummaryResponse newest = appointmentAt(2026, 3, 10);
        // appointment-service trie par date croissante (voir son propre code) : le gateway doit retrier.
        when(appointmentServiceClient.search(anyInt(), anyInt(), eq(1L)))
                .thenReturn(new AppointmentSummaryPage(List.of(oldest, middle, newest)));

        List<AppointmentSummaryResponse> recent = appointmentGateway.recent(1L);

        assertThat(recent).containsExactly(newest, middle, oldest);
    }

    @Test
    void keepsOnlyTheFiveMostRecent() {
        List<AppointmentSummaryResponse> sevenAppointments = List.of(
                appointmentAt(2020, 1, 1), appointmentAt(2021, 1, 1), appointmentAt(2022, 1, 1),
                appointmentAt(2023, 1, 1), appointmentAt(2024, 1, 1), appointmentAt(2025, 1, 1),
                appointmentAt(2026, 1, 1));
        when(appointmentServiceClient.search(anyInt(), anyInt(), eq(1L))).thenReturn(new AppointmentSummaryPage(sevenAppointments));

        assertThat(appointmentGateway.recent(1L)).hasSize(5);
    }

    @Test
    void toleratesAnEmptyHistory() {
        when(appointmentServiceClient.search(anyInt(), anyInt(), eq(1L))).thenReturn(new AppointmentSummaryPage(List.of()));

        assertThat(appointmentGateway.recent(1L)).isEmpty();
    }

    @Test
    void toleratesANullContentInThePage() {
        when(appointmentServiceClient.search(anyInt(), anyInt(), eq(1L))).thenReturn(new AppointmentSummaryPage(null));

        assertThat(appointmentGateway.recent(1L)).isEmpty();
    }

    private AppointmentSummaryResponse appointmentAt(int year, int month, int day) {
        return new AppointmentSummaryResponse(LocalDateTime.of(year, month, day, 9, 0), "Controle", "CONFIRME");
    }
}
