package tn.coconsult.medtrack.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.coconsult.medtrack.user.dto.ActivateRequest;
import tn.coconsult.medtrack.user.dto.InvitationRequest;
import tn.coconsult.medtrack.user.dto.InvitationResponse;
import tn.coconsult.medtrack.user.dto.UserResponse;
import tn.coconsult.medtrack.user.service.InvitationService;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur de gestion des invitations.
 * - POST /api/invitations, GET /api/invitations et PUT /api/invitations/{id}/revoke : réservés
 *   à l'ADMIN (envoi/renouvellement, liste avec statut réel, révocation).
 * - GET /api/invitations/{token} et POST /api/invitations/{token}/activate :
 *   endpoints publics, utilisables avant même la création du compte.
 */
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> invite(@Valid @RequestBody InvitationRequest request) {
        invitationService.createInvitation(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("message", "Invitation envoyée par email."));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InvitationResponse>> list() {
        return ResponseEntity.ok(invitationService.listInvitations());
    }

    @PutMapping("/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        invitationService.revokeInvitation(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Vérifie la validité d'un token d'invitation (public).
     * Retourne l'email pour pré-remplir l'UI, ou 404 / 409 / 410.
     */
    @GetMapping("/{token}")
    public ResponseEntity<Map<String, String>> checkToken(@PathVariable String token) {
        String email = invitationService.getInvitationEmail(token);
        return ResponseEntity.ok(Map.of("email", email));
    }

    @PostMapping("/{token}/activate")
    public ResponseEntity<UserResponse> activate(@PathVariable String token,
                                                 @Valid @RequestBody ActivateRequest request) {
        UserResponse user = invitationService.activateInvitation(token, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
}
