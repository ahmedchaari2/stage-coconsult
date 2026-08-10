package tn.coconsult.medtrack.appointment.repository;

import org.springframework.data.jpa.domain.Specification;
import tn.coconsult.medtrack.appointment.dto.AppointmentFilter;
import tn.coconsult.medtrack.appointment.model.Appointment;
import tn.coconsult.medtrack.appointment.model.StatutRendezVous;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    public static Specification<Appointment> hasPatientId(Long patientId) {
        if (patientId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("patientId"), patientId);
    }

    public static Specification<Appointment> hasPatientIdIn(List<Long> patientIds) {
        if (patientIds == null) {
            return null;
        }
        if (patientIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("patientId").in(patientIds);
    }

    public static Specification<Appointment> hasMedecinId(Long medecinId) {
        if (medecinId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("medecinId"), medecinId);
    }

    public static Specification<Appointment> hasStatut(StatutRendezVous statut) {
        if (statut == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("statut"), statut);
    }

    public static Specification<Appointment> dateHeureBetween(LocalDateTime dateFrom, LocalDateTime dateTo) {
        if (dateFrom == null && dateTo == null) {
            return null;
        }
        if (dateFrom != null && dateTo != null) {
            return (root, query, cb) -> cb.between(root.get("dateHeure"), dateFrom, dateTo);
        }
        if (dateFrom != null) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dateHeure"), dateFrom);
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dateHeure"), dateTo);
    }

    public static Specification<Appointment> matchesSearchTerm(String q, List<Long> matchingPatientIds) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> {
            var motifLike = cb.like(cb.lower(root.get("motif")), pattern);
            if (matchingPatientIds == null || matchingPatientIds.isEmpty()) {
                return motifLike;
            }
            return cb.or(root.get("patientId").in(matchingPatientIds), motifLike);
        };
    }

    public static Specification<Appointment> build(AppointmentFilter filter) {
        List<Specification<Appointment>> specs = new ArrayList<>();
        addIfPresent(specs, hasPatientId(filter.patientId()));
        addIfPresent(specs, hasMedecinId(filter.medecinId()));
        addIfPresent(specs, hasStatut(filter.statut()));
        addIfPresent(specs, dateHeureBetween(filter.dateFrom(), filter.dateTo()));

        return specs.stream().reduce(Specification::and).orElse(null);
    }

    private static void addIfPresent(List<Specification<Appointment>> specs, Specification<Appointment> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }
}
