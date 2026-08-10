package tn.coconsult.medtrack.user.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import tn.coconsult.medtrack.common.util.SearchTerms;
import tn.coconsult.medtrack.user.dto.UserFilter;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;

import java.util.ArrayList;
import java.util.List;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> hasNom(String nom) {
        if (!StringUtils.hasText(nom)) {
            return null;
        }
        String pattern = "%" + nom.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("nom")), pattern);
    }

    public static Specification<User> hasPrenom(String prenom) {
        if (!StringUtils.hasText(prenom)) {
            return null;
        }
        String pattern = "%" + prenom.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("prenom")), pattern);
    }

    public static Specification<User> hasEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.lower(root.get("email")), email.toLowerCase());
    }

    public static Specification<User> hasSpecialite(String specialite) {
        if (!StringUtils.hasText(specialite)) {
            return null;
        }
        String pattern = "%" + specialite.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("specialite")), pattern);
    }

    public static Specification<User> hasNumeroOrdre(String numeroOrdre) {
        if (!StringUtils.hasText(numeroOrdre)) {
            return null;
        }
        String pattern = "%" + numeroOrdre.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("numeroOrdre")), pattern);
    }

    public static Specification<User> hasActif(Boolean actif) {
        if (actif == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("actif"), actif);
    }

    /**
     * Une recherche à plusieurs mots ("Ben Salah") doit trouver un médecin dont le nom et le
     * prénom matchent chacun un mot différent : chaque mot est matché sur n'importe quel champ
     * (OR), tous les mots devant matcher pour retenir la ligne (AND entre les mots).
     */
    public static Specification<User> hasSearchTerm(String q) {
        List<String> tokens = SearchTerms.tokenize(q);
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.stream()
                .map(UserSpecifications::matchesAnySearchableField)
                .reduce(Specification::and)
                .orElse(null);
    }

    private static Specification<User> matchesAnySearchableField(String token) {
        String pattern = "%" + token + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nom")), pattern),
                cb.like(cb.lower(root.get("prenom")), pattern),
                cb.like(cb.lower(root.get("email")), pattern)
        );
    }

    public static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    public static Specification<User> hasSansSpecialite(Boolean sansSpecialite) {
        if (!Boolean.TRUE.equals(sansSpecialite)) {
            return null;
        }
        return (root, query, cb) -> cb.or(cb.isNull(root.get("specialite")), cb.equal(root.get("specialite"), ""));
    }

    public static Specification<User> hasSansNumeroOrdre(Boolean sansNumeroOrdre) {
        if (!Boolean.TRUE.equals(sansNumeroOrdre)) {
            return null;
        }
        return (root, query, cb) -> cb.or(cb.isNull(root.get("numeroOrdre")), cb.equal(root.get("numeroOrdre"), ""));
    }

    public static Specification<User> build(UserFilter filter) {
        List<Specification<User>> specs = new ArrayList<>();
        addIfPresent(specs, hasNom(filter.nom()));
        addIfPresent(specs, hasPrenom(filter.prenom()));
        addIfPresent(specs, hasEmail(filter.email()));
        addIfPresent(specs, hasSpecialite(filter.specialite()));
        addIfPresent(specs, hasNumeroOrdre(filter.numeroOrdre()));
        addIfPresent(specs, hasActif(filter.actif()));
        addIfPresent(specs, hasSearchTerm(filter.q()));
        addIfPresent(specs, hasSansSpecialite(filter.sansSpecialite()));
        addIfPresent(specs, hasSansNumeroOrdre(filter.sansNumeroOrdre()));

        return specs.stream().reduce(Specification::and).orElse(null);
    }

    private static void addIfPresent(List<Specification<User>> specs, Specification<User> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }
}
