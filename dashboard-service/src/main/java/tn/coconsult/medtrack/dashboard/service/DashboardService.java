package tn.coconsult.medtrack.dashboard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tn.coconsult.medtrack.dashboard.client.AppointmentGateway;
import tn.coconsult.medtrack.dashboard.client.MedicalRecordGateway;
import tn.coconsult.medtrack.dashboard.client.PatientGateway;
import tn.coconsult.medtrack.dashboard.client.PrescriptionGateway;
import tn.coconsult.medtrack.dashboard.client.UserGateway;
import tn.coconsult.medtrack.dashboard.dto.AdminDashboardResponse;
import tn.coconsult.medtrack.dashboard.dto.AdminDashboardResponse.ActiviteMedecin;
import tn.coconsult.medtrack.dashboard.dto.AdminDashboardResponse.AnnulationSemaine;
import tn.coconsult.medtrack.dashboard.dto.AdminDashboardResponse.NouveauxMois;
import tn.coconsult.medtrack.dashboard.dto.AdminDashboardResponse.RdvParJour;
import tn.coconsult.medtrack.dashboard.dto.AppointmentDto;
import tn.coconsult.medtrack.dashboard.dto.AujourdhuiResponse;
import tn.coconsult.medtrack.dashboard.dto.MedecinConsultationCountDto;
import tn.coconsult.medtrack.dashboard.dto.MedecinDashboardResponse;
import tn.coconsult.medtrack.dashboard.dto.MedecinDashboardResponse.PatientLite;
import tn.coconsult.medtrack.dashboard.dto.MedecinDashboardResponse.ProchainRdv;
import tn.coconsult.medtrack.dashboard.dto.MedecinDashboardResponse.TraitementEcheance;
import tn.coconsult.medtrack.dashboard.dto.AppointmentPage;
import tn.coconsult.medtrack.dashboard.dto.MedecinOptionDto;
import tn.coconsult.medtrack.dashboard.dto.PatientDto;
import tn.coconsult.medtrack.dashboard.dto.PatientPage;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Agrège les indicateurs des deux vues du dashboard. Chaque source est interrogée via un gateway
 * Feign protégé par circuit breaker ; toute panne est convertie en section {@code disponible=false}
 * (voir {@link #attempt}) plutôt qu'en échec global. Les données déjà chargées sont partagées entre
 * sections pour ne pas multiplier les appels.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final List<String> STATUTS = List.of("PLANIFIE", "CONFIRME", "ANNULE", "HONORE");
    private static final Set<String> STATUTS_A_VENIR = Set.of("PLANIFIE", "CONFIRME");
    private static final int ECHEANCE_JOURS = 7;
    private static final int ANNULATION_SEMAINES = 8;
    private static final int NOUVEAUX_MOIS_PROFONDEUR = 6;

    private final AppointmentGateway appointmentGateway;
    private final PatientGateway patientGateway;
    private final PrescriptionGateway prescriptionGateway;
    private final MedicalRecordGateway medicalRecordGateway;
    private final UserGateway userGateway;

    @Value("${medtrack.dashboard.occupation.heure-ouverture:08:00}")
    private String heureOuverture;

    @Value("${medtrack.dashboard.occupation.heure-fermeture:18:00}")
    private String heureFermeture;

    @Value("${medtrack.dashboard.occupation.duree-creneau-minutes:30}")
    private int dureeCreneauMinutes;


    public MedecinDashboardResponse medecinDashboard() {
        Long medecinId = currentUserId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Optional<PatientPage> referents = attempt(() -> patientGateway.search(medecinId, false));

        return new MedecinDashboardResponse(
                rendezVousJourSection(medecinId, today, now),
                patientsSuiviSection(referents, today),
                consultationsSection(medecinId, today),
                traitementsEcheanceSection(today),
                patientsSansDossierSection(referents));
    }

    private MedecinDashboardResponse.RendezVousJourSection rendezVousJourSection(Long medecinId, LocalDate today, LocalDateTime now) {
        return attempt(() -> appointmentGateway.search(medecinId, null, isoStart(today), isoEnd(today)))
                .map(page -> {
                    List<AppointmentDto> rdv = page.safeContent();
                    Map<String, Integer> parStatut = new LinkedHashMap<>();
                    STATUTS.forEach(s -> parStatut.put(s, 0));
                    rdv.forEach(a -> parStatut.merge(a.statut(), 1, Integer::sum));

                    ProchainRdv prochain = rdv.stream()
                            .filter(a -> a.dateHeure() != null && !a.dateHeure().isBefore(now) && STATUTS_A_VENIR.contains(a.statut()))
                            .min(Comparator.comparing(AppointmentDto::dateHeure))
                            .map(a -> new ProchainRdv(a.id(), a.dateHeure(), a.patientNom(), a.patientPrenom(), a.motif(), a.statut()))
                            .orElse(null);

                    return new MedecinDashboardResponse.RendezVousJourSection(true, rdv.size(), parStatut, prochain);
                })
                .orElseGet(MedecinDashboardResponse.RendezVousJourSection::unavailable);
    }

    private MedecinDashboardResponse.PatientsSuiviSection patientsSuiviSection(Optional<PatientPage> referents, LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        return referents
                .map(page -> {
                    long nouveaux = page.safeContent().stream()
                            .filter(p -> p.createdAt() != null && !p.createdAt().toLocalDate().isBefore(monthStart))
                            .count();
                    return new MedecinDashboardResponse.PatientsSuiviSection(true, page.totalElements(), nouveaux);
                })
                .orElseGet(MedecinDashboardResponse.PatientsSuiviSection::unavailable);
    }

    private MedecinDashboardResponse.ConsultationsSection consultationsSection(Long medecinId, LocalDate today) {
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth prevMonth = thisMonth.minusMonths(1);
        return attempt(() -> {
            long ceMois = countFor(medecinId, medicalRecordGateway.consultationCountByMedecin(
                    ISO_DATE.format(thisMonth.atDay(1)), ISO_DATE.format(thisMonth.atEndOfMonth())));
            long moisPrec = countFor(medecinId, medicalRecordGateway.consultationCountByMedecin(
                    ISO_DATE.format(prevMonth.atDay(1)), ISO_DATE.format(prevMonth.atEndOfMonth())));
            return new MedecinDashboardResponse.ConsultationsSection(true, ceMois, moisPrec, ceMois - moisPrec);
        }).orElseGet(MedecinDashboardResponse.ConsultationsSection::unavailable);
    }

    private MedecinDashboardResponse.TraitementsEcheanceSection traitementsEcheanceSection(LocalDate today) {
        LocalDate limite = today.plusDays(ECHEANCE_JOURS);
        return attempt(() -> prescriptionGateway.search(null, null, null, false))
                .map(page -> {
                    List<TraitementEcheance> items = page.safeContent().stream()
                            .filter(p -> p.consultationDate() != null && p.dureeJours() != null)
                            .map(p -> {
                                LocalDate fin = p.consultationDate().plusDays(p.dureeJours());
                                long restants = ChronoUnit.DAYS.between(today, fin);
                                return new TraitementEcheance(p.id(), p.medicament(), p.consultationDate(), p.dureeJours(), fin, restants);
                            })
                            .filter(t -> !t.dateFin().isBefore(today) && !t.dateFin().isAfter(limite))
                            .sorted(Comparator.comparing(TraitementEcheance::dateFin))
                            .toList();
                    return new MedecinDashboardResponse.TraitementsEcheanceSection(true, items.size(), items);
                })
                .orElseGet(MedecinDashboardResponse.TraitementsEcheanceSection::unavailable);
    }

    private MedecinDashboardResponse.PatientsSansDossierSection patientsSansDossierSection(Optional<PatientPage> referents) {
        if (referents.isEmpty()) {
            return MedecinDashboardResponse.PatientsSansDossierSection.unavailable();
        }
        return attempt(() -> Set.copyOf(medicalRecordGateway.patientIdsWithRecord()))
                .map(withRecord -> {
                    List<PatientLite> items = referents.get().safeContent().stream()
                            .filter(p -> !withRecord.contains(p.id()))
                            .map(p -> new PatientLite(p.id(), p.nom(), p.prenom(), p.numeroDossier()))
                            .toList();
                    return new MedecinDashboardResponse.PatientsSansDossierSection(true, items.size(), items);
                })
                .orElseGet(MedecinDashboardResponse.PatientsSansDossierSection::unavailable);
    }


    public AujourdhuiResponse aujourdhui() {
        User currentUser = currentUser();
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Optional<AppointmentPage> apptsToday = attempt(() -> appointmentGateway.search(null, null, isoStart(today), isoEnd(today)));
        if (apptsToday.isEmpty()) {
            return AujourdhuiResponse.unavailable();
        }

        List<AppointmentDto> rdv = apptsToday.get().safeContent().stream()
                .sorted(Comparator.comparing(AppointmentDto::dateHeure))
                .toList();

        long aConfirmer = rdv.stream().filter(a -> "PLANIFIE".equals(a.statut())).count();
        long annulesAujourdhui = rdv.stream().filter(a -> "ANNULE".equals(a.statut())).count();

        List<AujourdhuiResponse.RendezVousJourItem> items = rdv.stream()
                .map(a -> new AujourdhuiResponse.RendezVousJourItem(
                        a.id(), a.dateHeure(), a.statut(),
                        a.patientId(), a.patientNom(), a.patientPrenom(),
                        a.medecinId(), a.medecinNom(), a.medecinPrenom(),
                        a.motif()))
                .toList();

        List<AujourdhuiResponse.ProchainePlageLibre> plages = prochainesPlagesLibres(currentUser, isAdmin, rdv, today, now);

        return new AujourdhuiResponse(true, items, aConfirmer, annulesAujourdhui, plages);
    }

    private List<AujourdhuiResponse.ProchainePlageLibre> prochainesPlagesLibres(
            User currentUser, boolean isAdmin, List<AppointmentDto> rdvJour, LocalDate today, LocalDateTime now) {

        List<MedecinOptionDto> cibles;
        if (isAdmin) {
            Optional<List<MedecinOptionDto>> medecins = attempt(userGateway::allMedecins);
            if (medecins.isEmpty()) {
                return List.of();
            }
            cibles = medecins.get().stream().filter(m -> Boolean.TRUE.equals(m.actif())).toList();
        } else {
            cibles = List.of(new MedecinOptionDto(currentUser.getId(), currentUser.getNom(), currentUser.getPrenom(), null, null, true));
        }

        Map<Long, List<LocalDateTime>> occupePar = rdvJour.stream()
                .filter(a -> a.medecinId() != null && !"ANNULE".equals(a.statut()))
                .collect(Collectors.groupingBy(AppointmentDto::medecinId, Collectors.mapping(AppointmentDto::dateHeure, Collectors.toList())));

        return cibles.stream()
                .map(m -> {
                    LocalTime heure = prochaineHeureLibre(occupePar.getOrDefault(m.id(), List.of()), today, now);
                    return heure == null ? null : new AujourdhuiResponse.ProchainePlageLibre(m.id(), m.nom(), m.prenom(), heure);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private LocalTime prochaineHeureLibre(List<LocalDateTime> occupees, LocalDate today, LocalDateTime now) {
        Duration duree = Duration.ofMinutes(dureeCreneauMinutes);
        LocalDateTime debutJournee = today.atTime(LocalTime.parse(heureOuverture));
        LocalDateTime finJournee = today.atTime(LocalTime.parse(heureFermeture));
        LocalDateTime debut = now.isAfter(debutJournee) ? now : debutJournee;

        long minutesEcoulees = Duration.between(debutJournee, debut).toMinutes();
        long creneauxEcoules = (minutesEcoulees + dureeCreneauMinutes - 1) / dureeCreneauMinutes;
        LocalDateTime candidat = debutJournee.plusMinutes(creneauxEcoules * dureeCreneauMinutes);

        while (!candidat.plus(duree).isAfter(finJournee)) {
            LocalDateTime slot = candidat;
            boolean libre = occupees.stream().noneMatch(o -> Duration.between(o, slot).abs().compareTo(duree) < 0);
            if (libre) {
                return slot.toLocalTime();
            }
            candidat = candidat.plus(duree);
        }
        return null;
    }


    public AdminDashboardResponse adminDashboard(String periode) {
        LocalDate today = LocalDate.now();
        LocalDate[] fenetre = resolvePeriode(periode, today);
        LocalDate periodeStart = fenetre[0];
        LocalDate periodeEnd = fenetre[1];

        Optional<List<MedecinOptionDto>> medecins = attempt(userGateway::allMedecins);
        Optional<AppointmentPage> apptsPeriode =
                attempt(() -> appointmentGateway.search(null, null, isoStart(periodeStart), isoEnd(periodeEnd)));
        Optional<List<MedecinConsultationCountDto>> consultPeriode =
                attempt(() -> medicalRecordGateway.consultationCountByMedecin(ISO_DATE.format(periodeStart), ISO_DATE.format(periodeEnd)));
        Optional<PatientPage> actifs = attempt(() -> patientGateway.search(null, false));
        Optional<PatientPage> archives = attempt(() -> patientGateway.search(null, true));
        Optional<Set<Long>> withRecord = attempt(() -> Set.copyOf(medicalRecordGateway.patientIdsWithRecord()));

        return new AdminDashboardResponse(
                activiteParMedecinSection(medecins, apptsPeriode, consultPeriode, periodeStart, periodeEnd),
                occupationSection(medecins, today),
                annulationsSection(today),
                rendezVous7JoursSection(today),
                patientsStatsSection(actifs, archives),
                qualiteDonneesSection(actifs, medecins, withRecord));
    }

    private LocalDate[] resolvePeriode(String periode, LocalDate today) {
        if ("7j".equals(periode)) {
            return new LocalDate[]{today.minusDays(6), today};
        }
        if ("30j".equals(periode)) {
            return new LocalDate[]{today.minusDays(29), today};
        }
        YearMonth month = YearMonth.from(today);
        return new LocalDate[]{month.atDay(1), month.atEndOfMonth()};
    }

    AdminDashboardResponse.ActiviteParMedecinSection activiteParMedecinSection(
            Optional<List<MedecinOptionDto>> medecins, Optional<AppointmentPage> apptsMonth,
            Optional<List<MedecinConsultationCountDto>> consultMonth, LocalDate monthStart, LocalDate monthEnd) {

        if (medecins.isEmpty() || apptsMonth.isEmpty() || consultMonth.isEmpty()) {
            return AdminDashboardResponse.ActiviteParMedecinSection.unavailable();
        }

        Optional<Map<Long, Long>> prescByMedecin = attempt(() -> {
            Map<Long, Long> map = new LinkedHashMap<>();
            for (MedecinOptionDto m : medecins.get()) {
                map.put(m.id(), prescriptionGateway.count(m.id(), ISO_DATE.format(monthStart), ISO_DATE.format(monthEnd), false));
            }
            return map;
        });
        if (prescByMedecin.isEmpty()) {
            return AdminDashboardResponse.ActiviteParMedecinSection.unavailable();
        }

        Map<Long, Long> rdvByMedecin = apptsMonth.get().safeContent().stream()
                .filter(a -> a.medecinId() != null)
                .collect(Collectors.groupingBy(AppointmentDto::medecinId, Collectors.counting()));
        Map<Long, Long> consultByMedecin = consultMonth.get().stream()
                .collect(Collectors.toMap(MedecinConsultationCountDto::medecinId, MedecinConsultationCountDto::count, (a, b) -> a));

        List<ActiviteMedecin> items = medecins.get().stream()
                .map(m -> new ActiviteMedecin(m.id(), m.nom(), m.prenom(),
                        rdvByMedecin.getOrDefault(m.id(), 0L),
                        consultByMedecin.getOrDefault(m.id(), 0L),
                        prescByMedecin.get().getOrDefault(m.id(), 0L)))
                .toList();
        return new AdminDashboardResponse.ActiviteParMedecinSection(true, items);
    }

    AdminDashboardResponse.OccupationSection occupationSection(Optional<List<MedecinOptionDto>> medecins, LocalDate today) {
        LocalDate finSemaine = today.plusDays(6);
        Optional<AppointmentPage> apptsSemaine = attempt(() -> appointmentGateway.search(null, null, isoStart(today), isoEnd(finSemaine)));

        if (medecins.isEmpty() || apptsSemaine.isEmpty()) {
            return AdminDashboardResponse.OccupationSection.unavailable();
        }

        long medecinsActifs = medecins.get().stream().filter(m -> Boolean.TRUE.equals(m.actif())).count();
        long nonAnnules = apptsSemaine.get().safeContent().stream()
                .filter(a -> !"ANNULE".equals(a.statut()))
                .count();
        long theoriques = medecinsActifs * slotsParJour() * joursOuvresEntre(today, finSemaine);
        double taux = theoriques == 0 ? 0d : (double) nonAnnules / theoriques;
        String periode = ISO_DATE.format(today) + "/" + ISO_DATE.format(finSemaine);
        return new AdminDashboardResponse.OccupationSection(true, periode, theoriques, nonAnnules, round4(taux));
    }

    private AdminDashboardResponse.AnnulationsSection annulationsSection(LocalDate today) {
        LocalDate debut = today.minusWeeks(ANNULATION_SEMAINES).with(DayOfWeek.MONDAY);
        return attempt(() -> appointmentGateway.search(null, "ANNULE", isoStart(debut), isoEnd(today)))
                .map(page -> {
                    Map<String, long[]> parSemaine = new LinkedHashMap<>();
                    for (AppointmentDto a : page.safeContent()) {
                        if (a.dateHeure() == null) {
                            continue;
                        }
                        LocalDate jour = a.dateHeure().toLocalDate();
                        LocalDate lundi = jour.with(DayOfWeek.MONDAY);
                        String cle = jour.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", jour.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
                        parSemaine.computeIfAbsent(cle, k -> new long[]{lundi.toEpochDay(), 0})[1]++;
                    }
                    List<AnnulationSemaine> items = parSemaine.entrySet().stream()
                            .map(e -> new AnnulationSemaine(e.getKey(), LocalDate.ofEpochDay(e.getValue()[0]), e.getValue()[1]))
                            .sorted(Comparator.comparing(AnnulationSemaine::debut))
                            .toList();
                    return new AdminDashboardResponse.AnnulationsSection(true, items);
                })
                .orElseGet(AdminDashboardResponse.AnnulationsSection::unavailable);
    }

    private AdminDashboardResponse.RendezVous7JoursSection rendezVous7JoursSection(LocalDate today) {
        LocalDate fin = today.plusDays(6);
        return attempt(() -> appointmentGateway.search(null, null, isoStart(today), isoEnd(fin)))
                .map(page -> {
                    Map<LocalDate, Long> parJour = page.safeContent().stream()
                            .filter(a -> a.dateHeure() != null)
                            .collect(Collectors.groupingBy(a -> a.dateHeure().toLocalDate(), Collectors.counting()));
                    List<RdvParJour> items = new ArrayList<>();
                    for (int i = 0; i < 7; i++) {
                        LocalDate jour = today.plusDays(i);
                        items.add(new RdvParJour(jour, parJour.getOrDefault(jour, 0L)));
                    }
                    return new AdminDashboardResponse.RendezVous7JoursSection(true, items);
                })
                .orElseGet(AdminDashboardResponse.RendezVous7JoursSection::unavailable);
    }

    private AdminDashboardResponse.PatientsStatsSection patientsStatsSection(
            Optional<PatientPage> actifs, Optional<PatientPage> archives) {

        if (actifs.isEmpty() || archives.isEmpty()) {
            return AdminDashboardResponse.PatientsStatsSection.unavailable();
        }
        List<PatientDto> patientsActifs = actifs.get().safeContent();

        Map<String, Long> parSexe = new LinkedHashMap<>();
        parSexe.put("HOMME", 0L);
        parSexe.put("FEMME", 0L);
        parSexe.put("AUTRE", 0L);
        parSexe.put("NON_RENSEIGNE", 0L);
        for (PatientDto p : patientsActifs) {
            parSexe.merge(p.sexe() == null ? "NON_RENSEIGNE" : p.sexe(), 1L, Long::sum);
        }

        Map<String, Long> parAge = new LinkedHashMap<>();
        List.of("0-17", "18-39", "40-64", "65+").forEach(t -> parAge.put(t, 0L));
        LocalDate today = LocalDate.now();
        for (PatientDto p : patientsActifs) {
            if (p.dateNaissance() == null) {
                continue;
            }
            int age = (int) ChronoUnit.YEARS.between(p.dateNaissance(), today);
            parAge.merge(trancheAge(age), 1L, Long::sum);
        }

        Map<String, Long> parMois = new LinkedHashMap<>();
        YearMonth start = YearMonth.from(today).minusMonths(NOUVEAUX_MOIS_PROFONDEUR - 1L);
        for (int i = 0; i < NOUVEAUX_MOIS_PROFONDEUR; i++) {
            parMois.put(start.plusMonths(i).toString(), 0L);
        }
        for (PatientDto p : patientsActifs) {
            if (p.createdAt() == null) {
                continue;
            }
            String mois = YearMonth.from(p.createdAt().toLocalDate()).toString();
            if (parMois.containsKey(mois)) {
                parMois.merge(mois, 1L, Long::sum);
            }
        }
        List<NouveauxMois> nouveaux = parMois.entrySet().stream().map(e -> new NouveauxMois(e.getKey(), e.getValue())).toList();

        return new AdminDashboardResponse.PatientsStatsSection(true,
                actifs.get().totalElements(), archives.get().totalElements(), nouveaux, parSexe, parAge);
    }

    private AdminDashboardResponse.QualiteDonneesSection qualiteDonneesSection(
            Optional<PatientPage> actifs, Optional<List<MedecinOptionDto>> medecins, Optional<Set<Long>> withRecord) {

        if (actifs.isEmpty() || medecins.isEmpty() || withRecord.isEmpty()) {
            return AdminDashboardResponse.QualiteDonneesSection.unavailable();
        }
        List<PatientDto> patientsActifs = actifs.get().safeContent();
        long sansDossierNum = patientsActifs.stream().filter(p -> isBlank(p.numeroDossier())).count();
        long sansCin = patientsActifs.stream().filter(p -> isBlank(p.cin())).count();
        long medSansSpec = medecins.get().stream().filter(m -> isBlank(m.specialite())).count();
        long medSansOrdre = medecins.get().stream().filter(m -> isBlank(m.numeroOrdre())).count();
        long dossiersManquants = patientsActifs.stream().filter(p -> !withRecord.get().contains(p.id())).count();
        return new AdminDashboardResponse.QualiteDonneesSection(true,
                new AdminDashboardResponse.CompteurFiltrable(sansDossierNum, Map.of("sansNumeroDossier", "true")),
                new AdminDashboardResponse.CompteurFiltrable(sansCin, Map.of("sansCin", "true")),
                new AdminDashboardResponse.CompteurFiltrable(medSansSpec, Map.of("sansSpecialite", "true")),
                new AdminDashboardResponse.CompteurFiltrable(medSansOrdre, Map.of("sansNumeroOrdre", "true")),
                new AdminDashboardResponse.CompteurFiltrable(dossiersManquants, Map.of()));
    }


    private <T> Optional<T> attempt(Supplier<T> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private long countFor(Long medecinId, List<MedecinConsultationCountDto> counts) {
        return counts.stream().filter(c -> medecinId.equals(c.medecinId())).mapToLong(MedecinConsultationCountDto::count).sum();
    }

    private int slotsParJour() {
        LocalTime ouverture = LocalTime.parse(heureOuverture);
        LocalTime fermeture = LocalTime.parse(heureFermeture);
        long minutes = ChronoUnit.MINUTES.between(ouverture, fermeture);
        return dureeCreneauMinutes <= 0 ? 0 : (int) (minutes / dureeCreneauMinutes);
    }

    private int joursOuvresEntre(LocalDate debut, LocalDate fin) {
        int count = 0;
        for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
            DayOfWeek dow = jour.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }

    private String trancheAge(int age) {
        if (age < 18) return "0-17";
        if (age < 40) return "18-39";
        if (age < 65) return "40-64";
        return "65+";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    private String isoStart(LocalDate day) {
        return ISO_DATE_TIME.format(day.atStartOfDay());
    }

    private String isoEnd(LocalDate day) {
        return ISO_DATE_TIME.format(day.atTime(LocalTime.of(23, 59, 59)));
    }

    private Long currentUserId() {
        return currentUser().getId();
    }

    private User currentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
