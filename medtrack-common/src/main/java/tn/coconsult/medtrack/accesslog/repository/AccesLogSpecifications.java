package tn.coconsult.medtrack.accesslog.repository;

import org.springframework.data.jpa.domain.Specification;
import tn.coconsult.medtrack.accesslog.dto.AccesLogFilter;
import tn.coconsult.medtrack.accesslog.model.AccesLog;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.model.TypeAction;

import java.time.LocalDateTime;

public final class AccesLogSpecifications {

    private AccesLogSpecifications() {
    }

    public static Specification<AccesLog> build(AccesLogFilter filter) {
        Specification<AccesLog> spec = null;
        spec = and(spec, hasUtilisateurId(filter.utilisateurId()));
        spec = and(spec, hasPatientId(filter.patientId()));
        spec = and(spec, hasTypeRessource(filter.typeRessource()));
        spec = and(spec, hasAction(filter.action()));
        spec = and(spec, dateBetween(filter.dateFrom(), filter.dateTo()));
        return spec;
    }

    private static Specification<AccesLog> hasUtilisateurId(Long utilisateurId) {
        if (utilisateurId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("utilisateurId"), utilisateurId);
    }

    private static Specification<AccesLog> hasTypeRessource(TypeRessource typeRessource) {
        if (typeRessource == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("typeRessource"), typeRessource);
    }

    private static Specification<AccesLog> hasPatientId(Long patientId) {
        if (patientId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("patientId"), patientId);
    }

    private static Specification<AccesLog> hasAction(TypeAction action) {
        if (action == null) {
            return null;
        }
        if (action == TypeAction.VIEW) {
            return (root, query, cb) -> cb.or(cb.equal(root.get("action"), action), cb.isNull(root.get("action")));
        }
        return (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    private static Specification<AccesLog> dateBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            return null;
        }
        if (from != null && to != null) {
            return (root, query, cb) -> cb.between(root.get("dateHeure"), from, to);
        }
        if (from != null) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dateHeure"), from);
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dateHeure"), to);
    }

    private static Specification<AccesLog> and(Specification<AccesLog> base, Specification<AccesLog> next) {
        if (next == null) {
            return base;
        }
        return base == null ? next : base.and(next);
    }
}
