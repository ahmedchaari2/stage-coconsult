package tn.coconsult.medtrack.common.config;

/**
 * Identité de l'appelant, reconstruite à partir des seuls headers X-User-Id / X-User-Role posés
 * par la Gateway (voir {@link JwtAuthFilter}). Contrairement aux autres services header-trust,
 * dashboard-service n'a pas de base : il ne charge pas l'entité User, il fait confiance aux
 * headers tels quels (la Gateway a déjà validé le JWT, et chaque service appelé revérifie le
 * compte de son côté).
 */
public record AuthenticatedUser(Long id, String role) {
}
