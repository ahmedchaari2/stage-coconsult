package tn.coconsult.medtrack.dashboard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tn.coconsult.medtrack.dashboard.client.AppointmentGateway;
import tn.coconsult.medtrack.dashboard.client.MedicalRecordGateway;
import tn.coconsult.medtrack.dashboard.client.PatientGateway;
import tn.coconsult.medtrack.dashboard.client.PrescriptionGateway;
import tn.coconsult.medtrack.dashboard.client.UserGateway;
import tn.coconsult.medtrack.dashboard.dto.AdminDashboardResponse.OccupationSection;
import tn.coconsult.medtrack.dashboard.dto.AdminDashboardResponse.ActiviteParMedecinSection;
import tn.coconsult.medtrack.dashboard.dto.AppointmentDto;
import tn.coconsult.medtrack.dashboard.dto.AppointmentPage;
import tn.coconsult.medtrack.dashboard.dto.MedecinConsultationCountDto;
import tn.coconsult.medtrack.dashboard.dto.MedecinOptionDto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private AppointmentGateway appointmentGateway;
    @Mock
    private PatientGateway patientGateway;
    @Mock
    private PrescriptionGateway prescriptionGateway;
    @Mock
    private MedicalRecordGateway medicalRecordGateway;
    @Mock
    private UserGateway userGateway;

    private DashboardService dashboardService;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(appointmentGateway, patientGateway, prescriptionGateway, medicalRecordGateway, userGateway);
        ReflectionTestUtils.setField(dashboardService, "heureOuverture", "08:00");
        ReflectionTestUtils.setField(dashboardService, "heureFermeture", "10:00");
        ReflectionTestUtils.setField(dashboardService, "dureeCreneauMinutes", 60);
        today = LocalDate.of(2026, 8, 3).with(DayOfWeek.MONDAY);
    }

    @Test
    void semaineSansRendezVous_donneZeroPourcentSansExplosion() {
        List<MedecinOptionDto> medecins = List.of(medecin(1L, true));
        when(appointmentGateway.search(isNull(), isNull(), anyString(), anyString())).thenReturn(new AppointmentPage(List.of(), 0));

        OccupationSection result = dashboardService.occupationSection(Optional.of(medecins), today);

        assertThat(result.disponible()).isTrue();
        assertThat(result.creneauxTheoriques()).isEqualTo(10);
        assertThat(result.rendezVousNonAnnules()).isZero();
        assertThat(result.taux()).isZero();
    }

    @Test
    void aucunMedecinActif_denominateurNulNeCassePasLeCalcul() {
        List<MedecinOptionDto> medecins = List.of(medecin(1L, false));
        when(appointmentGateway.search(isNull(), isNull(), anyString(), anyString())).thenReturn(new AppointmentPage(List.of(), 0));

        OccupationSection result = dashboardService.occupationSection(Optional.of(medecins), today);

        assertThat(result.creneauxTheoriques()).isZero();
        assertThat(result.taux()).isZero();
    }

    @Test
    void semainePleine_donne100Pourcent() {
        List<MedecinOptionDto> medecins = List.of(medecin(1L, true));
        List<AppointmentDto> rdv = toutesLesPlagesOccupees(1L, today);
        when(appointmentGateway.search(isNull(), isNull(), anyString(), anyString()))
                .thenReturn(new AppointmentPage(rdv, rdv.size()));

        OccupationSection result = dashboardService.occupationSection(Optional.of(medecins), today);

        assertThat(result.creneauxTheoriques()).isEqualTo(10);
        assertThat(result.rendezVousNonAnnules()).isEqualTo(10);
        assertThat(result.taux()).isEqualTo(1.0);
    }

    @Test
    void medecinDesactive_exclDuDenominateur() {
        List<MedecinOptionDto> medecins = List.of(medecin(1L, true), medecin(2L, false));
        when(appointmentGateway.search(isNull(), isNull(), anyString(), anyString())).thenReturn(new AppointmentPage(List.of(), 0));

        OccupationSection result = dashboardService.occupationSection(Optional.of(medecins), today);

        assertThat(result.creneauxTheoriques()).isEqualTo(10);
    }

    @Test
    void rendezVousAnnules_neComptentPasCommeOccupes() {
        List<MedecinOptionDto> medecins = List.of(medecin(1L, true));
        AppointmentDto annule = new AppointmentDto(1L, 10L, "Nom", "Prenom", 1L, "Med", "Ecin",
                today.atTime(LocalTime.of(8, 0)), "motif", "ANNULE");
        when(appointmentGateway.search(isNull(), isNull(), anyString(), anyString()))
                .thenReturn(new AppointmentPage(List.of(annule), 1));

        OccupationSection result = dashboardService.occupationSection(Optional.of(medecins), today);

        assertThat(result.rendezVousNonAnnules()).isZero();
        assertThat(result.taux()).isZero();
    }

    @Test
    void activiteParMedecinUsesNonAuditingPrescriptionCount() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);
        List<MedecinOptionDto> medecins = List.of(medecin(4L, true));
        when(prescriptionGateway.count(4L, "2026-08-01", "2026-08-31", false)).thenReturn(7L);

        ActiviteParMedecinSection result = dashboardService.activiteParMedecinSection(
                Optional.of(medecins),
                Optional.of(new AppointmentPage(List.of(), 0)),
                Optional.of(List.of(new MedecinConsultationCountDto(4L, 3L))),
                start,
                end);

        assertThat(result.disponible()).isTrue();
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.medecinId()).isEqualTo(4L);
            assertThat(item.consultations()).isEqualTo(3L);
            assertThat(item.prescriptions()).isEqualTo(7L);
        });
        verify(prescriptionGateway).count(4L, "2026-08-01", "2026-08-31", false);
        verify(prescriptionGateway, never()).search(4L, "2026-08-01", "2026-08-31", false);
    }

    private MedecinOptionDto medecin(Long id, boolean actif) {
        return new MedecinOptionDto(id, "Nom" + id, "Prenom" + id, null, null, actif);
    }

    private List<AppointmentDto> toutesLesPlagesOccupees(Long medecinId, LocalDate lundi) {
        List<AppointmentDto> result = new ArrayList<>();
        long id = 1;
        for (int jour = 0; jour < 5; jour++) {
            LocalDate date = lundi.plusDays(jour);
            for (LocalTime heure : List.of(LocalTime.of(8, 0), LocalTime.of(9, 0))) {
                LocalDateTime dateHeure = date.atTime(heure);
                result.add(new AppointmentDto(id++, 10L, "Nom", "Prenom", medecinId, "Med", "Ecin", dateHeure, "motif", "CONFIRME"));
            }
        }
        return result;
    }
}
