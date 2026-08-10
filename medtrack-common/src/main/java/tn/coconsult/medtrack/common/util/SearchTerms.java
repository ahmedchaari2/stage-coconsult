package tn.coconsult.medtrack.common.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Découpe une recherche libre en mots. Une recherche "Haddad Hatem" doit trouver un patient
 * nom=Haddad/prenom=Hatem même si aucun champ à lui seul ne contient toute la phrase : chaque
 * mot est ensuite matché indépendamment (OR sur les champs), tous les mots devant matcher
 * (AND entre les mots) pour que la ligne soit retenue.
 */
public final class SearchTerms {

    private SearchTerms() {
    }

    public static List<String> tokenize(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return Arrays.stream(q.trim().split("\\s+"))
                .map(token -> token.toLowerCase(Locale.ROOT))
                .filter(token -> !token.isBlank())
                .toList();
    }
}
