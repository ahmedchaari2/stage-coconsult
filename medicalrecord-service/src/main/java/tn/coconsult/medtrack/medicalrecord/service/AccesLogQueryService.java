package tn.coconsult.medtrack.medicalrecord.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import tn.coconsult.medtrack.accesslog.dto.AccesLogFilter;
import tn.coconsult.medtrack.accesslog.model.AccesLog;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.repository.AccesLogRepository;
import tn.coconsult.medtrack.accesslog.repository.AccesLogSpecifications;
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.common.util.SearchTerms;
import tn.coconsult.medtrack.common.util.SortUtils;
import tn.coconsult.medtrack.medicalrecord.dto.AccesLogResponse;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccesLogQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "dateHeure", "dateHeure",
            "typeRessource", "typeRessource",
            "action", "action");

    private final AccesLogRepository accesLogRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    public PageDTO<AccesLogResponse> search(AccesLogFilter filter, int page, int size, String sort, String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort resolvedSort = SortUtils.resolve(sort, direction, SORTABLE_FIELDS, "dateHeure", Sort.Direction.DESC);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, resolvedSort);

        Specification<AccesLog> spec = AccesLogSpecifications.build(filter);
        Specification<AccesLog> searchSpec = buildSearchTermSpec(filter.q());
        if (searchSpec != null) {
            spec = spec == null ? searchSpec : spec.and(searchSpec);
        }
        Page<AccesLog> result = accesLogRepository.findAll(spec, pageRequest);
        return PageDTO.of(result, this::toResponse);
    }

    /**
     * AccesLog n'a pas de champ texte propre (patientId/utilisateurId sont de simples ids) : une
     * recherche "Haddad Hatem" doit donc résoudre le nom/prénom vers les patients ET les
     * utilisateurs correspondants avant de filtrer, un mot à la fois (AND entre les mots, OR entre
     * patient et utilisateur pour chaque mot).
     */
    private Specification<AccesLog> buildSearchTermSpec(String q) {
        List<String> tokens = SearchTerms.tokenize(q);
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.stream()
                .map(this::matchesSearchToken)
                .reduce(Specification::and)
                .orElse(null);
    }

    private Specification<AccesLog> matchesSearchToken(String token) {
        Specification<Patient> patientNameMatch = hasNom(token).or(hasPrenom(token));
        List<Long> matchingPatientIds = patientRepository.findAll(patientNameMatch).stream().map(Patient::getId).toList();

        Specification<User> userNameMatch = hasUserNom(token).or(hasUserPrenom(token));
        List<Long> matchingUtilisateurIds = userRepository.findAll(userNameMatch).stream().map(User::getId).toList();

        return (root, query, cb) -> cb.or(
                matchingPatientIds.isEmpty() ? cb.disjunction() : root.get("patientId").in(matchingPatientIds),
                matchingUtilisateurIds.isEmpty() ? cb.disjunction() : root.get("utilisateurId").in(matchingUtilisateurIds));
    }

    private static Specification<Patient> hasNom(String value) {
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("nom")), pattern);
    }

    private static Specification<Patient> hasPrenom(String value) {
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("prenom")), pattern);
    }

    private static Specification<User> hasUserNom(String value) {
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("nom")), pattern);
    }

    private static Specification<User> hasUserPrenom(String value) {
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("prenom")), pattern);
    }

    private AccesLogResponse toResponse(AccesLog entry) {
        Patient patient = entry.getPatientId() == null
                ? null
                : patientRepository.findById(entry.getPatientId()).orElse(null);
        User resourceUser = entry.getTypeRessource() == TypeRessource.MEDECIN
                ? userRepository.findById(entry.getRessourceId()).orElse(null)
                : null;
        return new AccesLogResponse(entry.getId(), entry.getUtilisateurId(), entry.getTypeRessource(),
                entry.getAction() == null ? TypeAction.VIEW : entry.getAction(),
                entry.getRessourceId(), entry.getPatientId(),
                patient == null ? null : patient.getNom(),
                patient == null ? null : patient.getPrenom(),
                resourceUser == null ? null : resourceUser.getNom(),
                resourceUser == null ? null : resourceUser.getPrenom(),
                entry.getDateHeure());
    }
}
