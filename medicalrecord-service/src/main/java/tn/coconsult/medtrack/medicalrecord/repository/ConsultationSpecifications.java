package tn.coconsult.medtrack.medicalrecord.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import tn.coconsult.medtrack.common.util.SearchTerms;
import tn.coconsult.medtrack.medicalrecord.dto.ConsultationFilter;
import tn.coconsult.medtrack.medicalrecord.model.Consultation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ConsultationSpecifications {

    private ConsultationSpecifications() {
    }

    public static Specification<Consultation> hasMedicalRecordId(Long medicalRecordId) {
        if (medicalRecordId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("medicalRecordId"), medicalRecordId);
    }

    public static Specification<Consultation> hasMedicalRecordIdIn(List<Long> medicalRecordIds) {
        if (medicalRecordIds == null) {
            return null;
        }
        if (medicalRecordIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("medicalRecordId").in(medicalRecordIds);
    }

    public static Specification<Consultation> hasMotif(String motif) {
        if (!StringUtils.hasText(motif)) {
            return null;
        }
        String pattern = "%" + motif.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("motif")), pattern);
    }

    public static Specification<Consultation> hasMedecinId(Long medecinId) {
        if (medecinId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("medecinId"), medecinId);
    }

    public static Specification<Consultation> dateBetween(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo == null) {
            return null;
        }
        if (dateFrom != null && dateTo != null) {
            return (root, query, cb) -> cb.between(root.get("consultationDate"), dateFrom, dateTo);
        }
        if (dateFrom != null) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("consultationDate"), dateFrom);
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("consultationDate"), dateTo);
    }

    /** Résolu par ConsultationService avant l'appel (jamais null en pratique) : archived=false par défaut. */
    public static Specification<Consultation> hasArchived(Boolean archived) {
        if (archived == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("archived"), archived);
    }

    /**
     * Recherche multi-mots sur motif et diagnostic : chaque mot doit matcher au moins un des deux
     * champs (OR), tous les mots devant matcher pour retenir la ligne (AND entre les mots).
     */
    public static Specification<Consultation> hasSearchTerm(String q) {
        List<String> tokens = SearchTerms.tokenize(q);
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.stream()
                .map(ConsultationSpecifications::matchesAnySearchableField)
                .reduce(Specification::and)
                .orElse(null);
    }

    private static Specification<Consultation> matchesAnySearchableField(String token) {
        String pattern = "%" + token + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("motif")), pattern),
                cb.like(cb.lower(root.get("diagnostic")), pattern));
    }

    public static Specification<Consultation> build(ConsultationFilter filter) {
        List<Specification<Consultation>> specs = new ArrayList<>();
        addIfPresent(specs, hasMotif(filter.motif()));
        addIfPresent(specs, hasMedecinId(filter.medecinId()));
        addIfPresent(specs, dateBetween(filter.dateFrom(), filter.dateTo()));
        addIfPresent(specs, hasArchived(filter.archived()));
        addIfPresent(specs, hasSearchTerm(filter.q()));

        return specs.stream().reduce(Specification::and).orElse(null);
    }

    private static void addIfPresent(List<Specification<Consultation>> specs, Specification<Consultation> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }
}
