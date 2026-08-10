package tn.coconsult.medtrack.appointment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.appointment.dto.AppointmentFilter;
import tn.coconsult.medtrack.appointment.dto.CreateAppointmentRequest;
import tn.coconsult.medtrack.appointment.dto.AppointmentResponse;
import tn.coconsult.medtrack.appointment.dto.UpdateAppointmentRequest;
import tn.coconsult.medtrack.appointment.mapper.AppointmentMapper;
import tn.coconsult.medtrack.appointment.model.Appointment;
import tn.coconsult.medtrack.appointment.model.StatutRendezVous;
import tn.coconsult.medtrack.appointment.client.AppointmentEventPublisher;
import tn.coconsult.medtrack.appointment.client.PatientReferentClient;
import tn.coconsult.medtrack.appointment.repository.AppointmentRepository;
import tn.coconsult.medtrack.appointment.repository.AppointmentSpecifications;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.accesslog.service.AccesLogService;
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.common.util.SearchTerms;
import tn.coconsult.medtrack.common.util.SortUtils;
import tn.coconsult.medtrack.events.AppointmentCreatedEvent;
import tn.coconsult.medtrack.events.AppointmentStatusChangedEvent;
import tn.coconsult.medtrack.notification.dto.AppointmentNotificationData;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "dateHeure", "dateHeure",
            "statut", "statut",
            "motif", "motif",
            "createdAt", "createdAt");

    // HONORE et ANNULE sont des états terminaux, pas d'entrée dans la map = aucune sortie possible.
    private static final Map<StatutRendezVous, Set<StatutRendezVous>> TRANSITIONS_AUTORISEES = Map.of(
            StatutRendezVous.PLANIFIE, Set.of(StatutRendezVous.CONFIRME, StatutRendezVous.HONORE, StatutRendezVous.ANNULE),
            StatutRendezVous.CONFIRME, Set.of(StatutRendezVous.HONORE, StatutRendezVous.ANNULE),
            StatutRendezVous.HONORE, Set.of(),
            StatutRendezVous.ANNULE, Set.of());

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final PatientReferentClient patientReferentClient;
    private final AppointmentEventPublisher appointmentEventPublisher;
    private final AccesLogService accesLogService;
    private final AppointmentMapper appointmentMapper;

    @Value("${medtrack.appointment.duree-creneau-minutes:30}")
    private int dureeCreneauMinutes;

    @Value("${medtrack.appointment.heure-ouverture:08:00}")
    private String heureOuverture;

    @Value("${medtrack.appointment.heure-fermeture:18:00}")
    private String heureFermeture;

    public AppointmentResponse create(CreateAppointmentRequest request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient introuvable"));
        requireAccess(request.getPatientId());
        if (request.getDateHeure().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date du rendez-vous ne peut pas être dans le passé");
        }
        requireOpenDay(request.getDateHeure().toLocalDate());

        Long medecinId = resolveMedecinId(request.getMedecinId());
        requireActiveMedecin(medecinId);
        requireCreneauLibre(medecinId, request.getDateHeure(), null);

        Appointment appointment = new Appointment();
        appointment.setPatientId(request.getPatientId());
        appointment.setMedecinId(medecinId);
        appointment.setDateHeure(request.getDateHeure());
        appointment.setMotif(request.getMotif());
        appointment.setNotes(request.getNotes());
        appointment.setStatut(StatutRendezVous.PLANIFIE);

        Appointment saved = appointmentRepository.save(appointment);
        logAcces(saved, TypeAction.CREATE);

        User medecin = userRepository.findById(saved.getMedecinId()).orElse(null);
        appointmentEventPublisher.publishCreated(new AppointmentCreatedEvent(
                saved.getId(), saved.getMedecinId(), toData(patient, medecin, saved.getDateHeure(), null)));

        return enrichOne(saved);
    }

    public PageDTO<AppointmentResponse> search(AppointmentFilter filter, int page, int size, String sort, String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort resolvedSort = SortUtils.resolve(sort, direction, SORTABLE_FIELDS, "dateHeure", Sort.Direction.ASC);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, resolvedSort);

        Specification<Appointment> spec = buildScopedSpec(filter);
        Page<Appointment> result = appointmentRepository.findAll(spec, pageRequest);
        List<AppointmentResponse> content = enrichBatch(result.getContent());
        return new PageDTO<>(content, result.getNumber(), result.getSize(), result.getTotalPages(), result.getTotalElements());
    }

    public List<AppointmentResponse> calendar(YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atEndOfMonth().atTime(LocalTime.MAX);
        AppointmentFilter filter = new AppointmentFilter(null, null, null, start, end, null);

        Specification<Appointment> spec = buildScopedSpec(filter);
        List<Appointment> result = appointmentRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "dateHeure"));
        return enrichBatch(result);
    }

    /**
     * Créneaux libres d'un médecin pour une journée, au format HH:mm : de l'heure d'ouverture à
     * l'heure de fermeture par pas d'une durée de créneau, moins ceux qui entreraient en conflit
     * avec un rendez-vous existant. Même règle de conflit que {@link #requireCreneauLibre}, pour
     * ne jamais proposer au frontend un créneau que la création refuserait ensuite en 409.
     */
    public List<String> disponibilites(Long medecinId, LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return List.of();
        }
        Long resolvedMedecinId = resolveMedecinId(medecinId);
        Duration duree = Duration.ofMinutes(dureeCreneauMinutes);

        List<LocalDateTime> occupes = appointmentRepository
                .findByMedecinIdAndStatutNotAndDateHeureAfterAndDateHeureBefore(
                        resolvedMedecinId, StatutRendezVous.ANNULE,
                        date.atStartOfDay().minus(duree), date.atTime(LocalTime.MAX).plus(duree))
                .stream()
                .map(Appointment::getDateHeure)
                .toList();

        LocalDateTime maintenant = LocalDateTime.now();
        LocalDateTime fermeture = date.atTime(LocalTime.parse(heureFermeture));
        List<String> creneaux = new ArrayList<>();
        for (LocalDateTime creneau = date.atTime(LocalTime.parse(heureOuverture));
             !creneau.plus(duree).isAfter(fermeture);
             creneau = creneau.plus(duree)) {
            if (creneau.isBefore(maintenant)) {
                continue;
            }
            LocalDateTime candidat = creneau;
            boolean libre = occupes.stream()
                    .noneMatch(occupe -> Duration.between(occupe, candidat).abs().compareTo(duree) < 0);
            if (libre) {
                creneaux.add(creneau.format(HEURE));
            }
        }
        return creneaux;
    }

    public AppointmentResponse findById(Long id) {
        Appointment appointment = findAppointmentOrThrow(id);
        logAcces(appointment, TypeAction.VIEW);
        return enrichOne(appointment);
    }

    private void logAcces(Appointment appointment, TypeAction action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();
        accesLogService.enregistrerAcces(
                currentUser.getId(), TypeRessource.RENDEZ_VOUS, appointment.getId(), appointment.getPatientId(), action);
    }

    public AppointmentResponse update(Long id, UpdateAppointmentRequest request) {
        Appointment appointment = findAppointmentOrThrow(id);
        StatutRendezVous previousStatut = appointment.getStatut();
        LocalDateTime previousDateHeure = appointment.getDateHeure();

        Long medecinId = resolveMedecinId(request.getMedecinId());
        boolean dateChanged = !appointment.getDateHeure().equals(request.getDateHeure());
        if (dateChanged) {
            if (request.getDateHeure().isBefore(LocalDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La date du rendez-vous ne peut pas être dans le passé");
            }
            requireOpenDay(request.getDateHeure().toLocalDate());
        }
        if (!medecinId.equals(appointment.getMedecinId())) {
            requireActiveMedecin(medecinId);
        }
        if (request.getStatut() != StatutRendezVous.ANNULE) {
            requireCreneauLibre(medecinId, request.getDateHeure(), id);
        }

        if (request.getStatut() != null && request.getStatut() != appointment.getStatut()) {
            requireValidTransition(appointment.getStatut(), request.getStatut());
        }

        appointment.setMedecinId(medecinId);
        appointment.setDateHeure(request.getDateHeure());
        appointment.setMotif(request.getMotif());
        appointment.setNotes(request.getNotes());
        appointment.setStatut(request.getStatut());

        if (!appointment.getDateHeure().equals(previousDateHeure)) {
            appointment.setRappelEnvoye(false);
            appointment.setRappelVeilleEnvoye(false);
        }

        Appointment saved = appointmentRepository.save(appointment);
        logAcces(saved, saved.getStatut() != previousStatut ? TypeAction.STATUS_CHANGE : TypeAction.UPDATE);

        if (saved.getStatut() != previousStatut && saved.getStatut() != StatutRendezVous.PLANIFIE) {
            Patient patient = patientRepository.findById(saved.getPatientId()).orElse(null);
            User medecin = userRepository.findById(saved.getMedecinId()).orElse(null);
            appointmentEventPublisher.publishStatusChanged(new AppointmentStatusChangedEvent(
                    saved.getId(), saved.getMedecinId(), toData(patient, medecin, saved.getDateHeure(), saved.getStatut())));
        }

        return enrichOne(saved);
    }

    public void delete(Long id) {
        Appointment appointment = findAppointmentOrThrow(id);
        appointmentRepository.delete(appointment);
        logAcces(appointment, TypeAction.DELETE);
    }

    public boolean isMedecinReferentForAppointment(Long appointmentId, Long medecinId) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return false;
        }
        if (medecinId != null && medecinId.equals(appointment.getMedecinId())) {
            return true;
        }
        return patientReferentClient.isReferent(appointment.getPatientId(), medecinId);
    }

    /**
     * Contrôle d'accès patientId à la création, en Java plutôt qu'en @PreAuthorize SpEL :
     * patient-service (et son bean isMedecinReferent) ne vit plus dans ce service, l'appel se
     * fait via Feign.
     */
    private void requireAccess(Long patientId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() == Role.MEDECIN && patientReferentClient.isReferent(patientId, currentUser.getId())) {
            return;
        }
        throw new AccessDeniedException("Accès refusé : ce patient ne vous est pas assigné");
    }

    private void requireCreneauLibre(Long medecinId, LocalDateTime dateHeure, Long excludeId) {
        Duration duree = Duration.ofMinutes(dureeCreneauMinutes);
        appointmentRepository
                .findByMedecinIdAndStatutNotAndDateHeureAfterAndDateHeureBefore(
                        medecinId, StatutRendezVous.ANNULE, dateHeure.minus(duree), dateHeure.plus(duree))
                .stream()
                .filter(conflit -> !conflit.getId().equals(excludeId))
                .findFirst()
                .ifPresent(conflit -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Le médecin a déjà un rendez-vous à " + conflit.getDateHeure().format(HEURE));
                });
    }

    private Appointment findAppointmentOrThrow(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rendez-vous introuvable"));
    }

    private Specification<Appointment> buildScopedSpec(AppointmentFilter filter) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();

        Specification<Appointment> spec = AppointmentSpecifications.build(filter);

        Specification<Appointment> qSpec = buildSearchTermSpec(filter.q());
        if (qSpec != null) {
            spec = (spec == null) ? qSpec : spec.and(qSpec);
        }

        if (currentUser.getRole() == Role.MEDECIN) {
            List<Long> referentPatientIds = patientRepository
                    .findAll(hasMedecinReferentId(currentUser.getId()))
                    .stream()
                    .map(Patient::getId)
                    .toList();
            Specification<Appointment> scopeSpec = AppointmentSpecifications.hasPatientIdIn(referentPatientIds)
                    .or(AppointmentSpecifications.hasMedecinId(currentUser.getId()));
            spec = (spec == null) ? scopeSpec : spec.and(scopeSpec);
        }

        return spec;
    }

    /**
     * Une recherche à plusieurs mots ("Haddad Hatem") doit trouver les rendez-vous d'un patient
     * dont le nom et le prénom matchent chacun un mot différent : chaque mot est résolu
     * indépendamment, tous les mots devant matcher (AND) pour retenir la ligne.
     */
    private Specification<Appointment> buildSearchTermSpec(String q) {
        List<String> tokens = SearchTerms.tokenize(q);
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.stream()
                .map(this::matchesSearchToken)
                .reduce(Specification::and)
                .orElse(null);
    }

    private Specification<Appointment> matchesSearchToken(String token) {
        Specification<Patient> nameMatch = hasNom(token).or(hasPrenom(token));
        List<Long> matchingPatientIds = patientRepository.findAll(nameMatch).stream().map(Patient::getId).toList();
        return AppointmentSpecifications.matchesSearchTerm(token, matchingPatientIds);
    }

    private Long resolveMedecinId(Long requestedMedecinId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getRole() == Role.MEDECIN) {
            return currentUser.getId();
        }
        if (requestedMedecinId == null) {
            throw new IllegalArgumentException("medecinId est obligatoire pour un ADMIN");
        }
        return requestedMedecinId;
    }

    private void requireActiveMedecin(Long medecinId) {
        User medecin = userRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable"));
        if (medecin.getRole() != Role.MEDECIN) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "L'utilisateur sélectionné n'est pas un médecin");
        }
        if (Boolean.FALSE.equals(medecin.getActif())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Le médecin est inactif");
        }
    }

    private void requireOpenDay(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Les rendez-vous ne peuvent pas être planifiés le dimanche");
        }
    }

    private void requireValidTransition(StatutRendezVous actuel, StatutRendezVous demande) {
        if (!TRANSITIONS_AUTORISEES.get(actuel).contains(demande)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de faire passer un rendez-vous " + statutLabel(actuel) + " au statut " + statutLabel(demande) + ".");
        }
    }

    private static String statutLabel(StatutRendezVous statut) {
        return switch (statut) {
            case PLANIFIE -> "planifié";
            case CONFIRME -> "confirmé";
            case HONORE -> "honoré";
            case ANNULE -> "annulé";
        };
    }

    private AppointmentResponse enrichOne(Appointment appointment) {
        return enrichBatch(List.of(appointment)).get(0);
    }

    private List<AppointmentResponse> enrichBatch(List<Appointment> appointments) {
        List<AppointmentResponse> responses = appointments.stream().map(appointmentMapper::toResponse).toList();

        Set<Long> patientIds = responses.stream().map(AppointmentResponse::getPatientId).collect(Collectors.toSet());
        Set<Long> medecinIds = responses.stream().map(AppointmentResponse::getMedecinId).collect(Collectors.toSet());

        Map<Long, Patient> patients = patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, p -> p));
        Map<Long, User> medecins = userRepository.findAllById(medecinIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        responses.forEach(response -> {
            Patient patient = patients.get(response.getPatientId());
            if (patient != null) {
                response.setPatientNom(patient.getNom());
                response.setPatientPrenom(patient.getPrenom());
            }
            User medecin = medecins.get(response.getMedecinId());
            if (medecin != null) {
                response.setMedecinNom(medecin.getNom());
                response.setMedecinPrenom(medecin.getPrenom());
            }
        });

        return responses;
    }

    private static Specification<Patient> hasMedecinReferentId(Long medecinReferentId) {
        if (medecinReferentId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("medecinReferentId"), medecinReferentId);
    }

    private static Specification<Patient> hasNom(String nom) {
        if (!StringUtils.hasText(nom)) {
            return null;
        }
        String pattern = "%" + nom.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("nom")), pattern);
    }

    private static Specification<Patient> hasPrenom(String prenom) {
        if (!StringUtils.hasText(prenom)) {
            return null;
        }
        String pattern = "%" + prenom.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("prenom")), pattern);
    }

    /** Jointure nom/prenom patient+médecin -> AppointmentNotificationData, embarquée dans l'évènement RabbitMQ. */
    private static AppointmentNotificationData toData(Patient patient, User medecin, LocalDateTime dateHeure, StatutRendezVous statut) {
        return new AppointmentNotificationData(
                patient == null ? null : patient.getNom(),
                patient == null ? null : patient.getPrenom(),
                medecin == null ? null : medecin.getNom(),
                medecin == null ? null : medecin.getPrenom(),
                dateHeure,
                statut);
    }
}
