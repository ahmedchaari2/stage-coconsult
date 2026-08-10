package tn.coconsult.medtrack.patient.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import tn.coconsult.medtrack.common.util.SearchTerms;
import tn.coconsult.medtrack.patient.dto.PatientFilter;
import tn.coconsult.medtrack.patient.model.Patient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PatientSpecifications {

    private PatientSpecifications() {
    }

    public static Specification<Patient> hasNom(String nom) {
        if (!StringUtils.hasText(nom)) {
            return null;
        }
        String pattern = "%" + nom.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("nom")), pattern);
    }

    public static Specification<Patient> hasPrenom(String prenom) {
        if (!StringUtils.hasText(prenom)) {
            return null;
        }
        String pattern = "%" + prenom.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("prenom")), pattern);
    }

    public static Specification<Patient> hasEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.lower(root.get("email")), email.toLowerCase());
    }

    public static Specification<Patient> hasMedecinReferentId(Long medecinReferentId) {
        if (medecinReferentId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("medecinReferentId"), medecinReferentId);
    }

    public static Specification<Patient> hasTelephone(String telephone) {
        if (!StringUtils.hasText(telephone)) {
            return null;
        }
        String pattern = "%" + telephone.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("telephone")), pattern);
    }

    public static Specification<Patient> hasNumeroDossier(String numeroDossier) {
        if (!StringUtils.hasText(numeroDossier)) {
            return null;
        }
        String pattern = "%" + numeroDossier.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("numeroDossier")), pattern);
    }

    public static Specification<Patient> hasDateNaissanceBetween(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        if (from != null && to != null) {
            return (root, query, cb) -> cb.between(root.get("dateNaissance"), from, to);
        }
        if (from != null) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dateNaissance"), from);
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dateNaissance"), to);
    }

    /** Résolu par PatientService avant l'appel (jamais null en pratique) : archived=false par défaut. */
    public static Specification<Patient> hasArchived(Boolean archived) {
        if (archived == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("archived"), archived);
    }

    public static Specification<Patient> hasSansNumeroDossier(Boolean sansNumeroDossier) {
        if (!Boolean.TRUE.equals(sansNumeroDossier)) {
            return null;
        }
        return (root, query, cb) -> cb.or(cb.isNull(root.get("numeroDossier")), cb.equal(root.get("numeroDossier"), ""));
    }

    public static Specification<Patient> hasSansCin(Boolean sansCin) {
        if (!Boolean.TRUE.equals(sansCin)) {
            return null;
        }
        return (root, query, cb) -> cb.or(cb.isNull(root.get("cin")), cb.equal(root.get("cin"), ""));
    }

    /**
     * Une recherche à plusieurs mots ("Haddad Hatem") doit trouver un patient dont le nom et le
     * prénom matchent chacun un mot différent, même si aucun champ ne contient toute la phrase :
     * chaque mot est matché indépendamment sur n'importe quel champ (OR), tous les mots devant
     * matcher pour retenir la ligne (AND entre les mots).
     */
    public static Specification<Patient> hasSearchTerm(String q) {
        List<String> tokens = SearchTerms.tokenize(q);
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.stream()
                .map(PatientSpecifications::matchesAnySearchableField)
                .reduce(Specification::and)
                .orElse(null);
    }

    private static Specification<Patient> matchesAnySearchableField(String token) {
        String pattern = "%" + token + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nom")), pattern),
                cb.like(cb.lower(root.get("prenom")), pattern),
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("telephone")), pattern),
                cb.like(cb.lower(root.get("numeroDossier")), pattern)
        );
    }

    public static Specification<Patient> build(PatientFilter filter) {
        List<Specification<Patient>> specs = new ArrayList<>();
        addIfPresent(specs, hasNom(filter.nom()));
        addIfPresent(specs, hasPrenom(filter.prenom()));
        addIfPresent(specs, hasEmail(filter.email()));
        addIfPresent(specs, hasMedecinReferentId(filter.medecinReferentId()));
        addIfPresent(specs, hasTelephone(filter.telephone()));
        addIfPresent(specs, hasNumeroDossier(filter.numeroDossier()));
        addIfPresent(specs, hasDateNaissanceBetween(filter.dateNaissanceFrom(), filter.dateNaissanceTo()));
        addIfPresent(specs, hasArchived(filter.archived()));
        addIfPresent(specs, hasSearchTerm(filter.q()));
        addIfPresent(specs, hasSansNumeroDossier(filter.sansNumeroDossier()));
        addIfPresent(specs, hasSansCin(filter.sansCin()));

        return specs.stream().reduce(Specification::and).orElse(null);
    }

    private static void addIfPresent(List<Specification<Patient>> specs, Specification<Patient> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }
}
