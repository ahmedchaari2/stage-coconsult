package tn.coconsult.medtrack.common.util;

import org.springframework.data.domain.Sort;

import java.util.Map;

public final class SortUtils {

    private SortUtils() {
    }

    /**
     * Résout un tri client (clé publique + direction) vers un Sort JPA sûr.
     * `allowedFields` mappe une clé publique (ex. "nom") vers le nom réel de la
     * propriété de l'entité (ex. "nom") : évite d'exposer n'importe quel chemin
     * de propriété JPA tel quel dans l'URL.
     */
    public static Sort resolve(String sort, String direction, Map<String, String> allowedFields,
                                String defaultField, Sort.Direction defaultDirection) {
        String property = sort != null ? allowedFields.get(sort) : null;
        if (property == null) {
            property = defaultField;
        }
        Sort.Direction dir = defaultDirection;
        if ("asc".equalsIgnoreCase(direction)) {
            dir = Sort.Direction.ASC;
        } else if ("desc".equalsIgnoreCase(direction)) {
            dir = Sort.Direction.DESC;
        }
        return Sort.by(dir, property);
    }
}
