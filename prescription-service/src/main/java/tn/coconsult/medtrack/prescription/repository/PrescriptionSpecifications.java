package tn.coconsult.medtrack.prescription.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import tn.coconsult.medtrack.prescription.dto.PrescriptionFilter;
import tn.coconsult.medtrack.prescription.model.Prescription;

import java.util.ArrayList;
import java.util.List;

public final class PrescriptionSpecifications {

    private PrescriptionSpecifications() {
    }

    public static Specification<Prescription> hasConsultationId(Long consultationId) {
        if (consultationId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("consultationId"), consultationId);
    }

    public static Specification<Prescription> hasConsultationIdIn(List<Long> consultationIds) {
        if (consultationIds == null) {
            return null;
        }
        if (consultationIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("consultationId").in(consultationIds);
    }

    public static Specification<Prescription> hasMedicament(String medicament) {
        if (!StringUtils.hasText(medicament)) {
            return null;
        }
        String pattern = "%" + medicament.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("medicament")), pattern);
    }

    public static Specification<Prescription> hasArchived(Boolean archived) {
        if (archived == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("archived"), archived);
    }

    public static Specification<Prescription> hasIdIn(List<Long> ids) {
        if (ids == null) {
            return null;
        }
        if (ids.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("id").in(ids);
    }

    public static Specification<Prescription> matchesSearchTerm(String q, List<Long> matchingConsultationIds) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> {
            var medicamentLike = cb.like(cb.lower(root.get("medicament")), pattern);
            if (matchingConsultationIds == null || matchingConsultationIds.isEmpty()) {
                return medicamentLike;
            }
            return cb.or(root.get("consultationId").in(matchingConsultationIds), medicamentLike);
        };
    }

    /** Combine uniquement les filtres directs sur Prescription (medicament/archived). */
    public static Specification<Prescription> build(PrescriptionFilter filter) {
        List<Specification<Prescription>> specs = new ArrayList<>();
        addIfPresent(specs, hasMedicament(filter.medicament()));
        addIfPresent(specs, hasArchived(filter.archived()));

        return specs.stream().reduce(Specification::and).orElse(null);
    }

    private static void addIfPresent(List<Specification<Prescription>> specs, Specification<Prescription> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }
}
