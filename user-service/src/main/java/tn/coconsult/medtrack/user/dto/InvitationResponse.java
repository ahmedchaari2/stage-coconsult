package tn.coconsult.medtrack.user.dto;

import tn.coconsult.medtrack.user.model.InvitationStatus;
import tn.coconsult.medtrack.user.model.Role;

import java.time.LocalDateTime;

/**
 * Vue d'une invitation pour la liste ADMIN (GET /api/invitations). `status` est recalculé à la
 * lecture (voir InvitationService.toResponse) : une invitation PENDING dont la date est dépassée
 * s'affiche EXPIRED même si la ligne en base n'a pas encore été basculée par un appel sur son
 * token — l'ADMIN doit voir l'état réel, pas un état qui n'a pas encore été rafraîchi.
 */
public record InvitationResponse(
        Long id,
        String email,
        Role role,
        InvitationStatus status,
        LocalDateTime expirationDate,
        LocalDateTime createdAt,
        String invitedByName) {
}
