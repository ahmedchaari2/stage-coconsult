package tn.coconsult.medtrack.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Sort;
import tn.coconsult.medtrack.user.dto.ActivateRequest;
import tn.coconsult.medtrack.user.dto.InvitationRequest;
import tn.coconsult.medtrack.user.dto.InvitationResponse;
import tn.coconsult.medtrack.user.dto.UserResponse;
import tn.coconsult.medtrack.user.mapper.UserMapper;
import tn.coconsult.medtrack.user.model.Invitation;
import tn.coconsult.medtrack.user.model.InvitationStatus;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.InvitationRepository;
import tn.coconsult.medtrack.user.repository.UserRepository;
import tn.coconsult.medtrack.user.util.EmailNormalizer;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Value("${medtrack.frontend.url}")
    private String frontendUrl;

    /**
     * Crée une invitation pour un médecin puis déclenche l'email d'activation, ou renouvelle
     * l'invitation existante si l'email en a déjà une non acceptée (PENDING ou EXPIRED) : même
     * ligne réutilisée (nouveau token, nouvelle expiration, statut remis à PENDING), pas de doublon
     * empilé. C'est ce qui permet à un ADMIN de relancer une invitation dont le lien a expiré ou
     * dont l'email n'est jamais arrivé, sans quoi l'email resterait bloqué jusqu'à la fin des temps
     * dès qu'une invitation existe une fois pour lui. Envoi asynchrone (voir EmailService.
     * sendHtmlEmail) : la requête répond dès l'invitation persistée, sans attendre le SMTP.
     * Le @Transactional ne couvre plus que la persistance (recherche + save atomiques).
     */
    @Transactional
    public void createInvitation(InvitationRequest request) {
        String email = EmailNormalizer.normalize(request.getEmail());

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            throw new IllegalArgumentException("Cet email est déjà utilisé");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User admin = (User) authentication.getPrincipal();

        List<Invitation> nonAccepted = invitationRepository.findByEmailAndStatusNot(email, InvitationStatus.ACCEPTED);
        Invitation invitation = nonAccepted.stream()
                .max(Comparator.comparing(Invitation::getCreatedAt))
                .orElseGet(Invitation::new);

        invitation.setEmail(email);
        invitation.setRole(Role.MEDECIN);
        invitation.setInvitedBy(admin);
        invitation.setStatus(InvitationStatus.PENDING);
        // vers l'ancien token, déjà expiré.
        invitation.setToken(UUID.randomUUID().toString());
        invitation.setExpirationDate(LocalDateTime.now().plusHours(48));
        Invitation saved = invitationRepository.save(invitation);

        String activationLink = frontendUrl + "/invitation/" + saved.getToken();
        String subject = "Medtrack - Invitation à rejoindre la plateforme";
        emailService.sendHtmlEmail(email, subject, buildInvitationEmailHtml(admin, saved.getRole(), activationLink));
    }

    /**
     * Révoque une invitation (ADMIN uniquement) : le lien devient inutilisable, sans supprimer la
     * ligne (traçabilité). Une invitation déjà ACCEPTED ne se révoque pas (le compte existe déjà,
     * ça n'aurait aucun effet) ; une invitation déjà REVOKED est idempotente.
     */
    @Transactional
    public void revokeInvitation(Long id) {
        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation introuvable"));

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette invitation a déjà été acceptée, elle ne peut plus être révoquée");
        }

        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);
    }

    public List<InvitationResponse> listInvitations() {
        return invitationRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toInvitationResponse)
                .toList();
    }

    private InvitationResponse toInvitationResponse(Invitation invitation) {
        InvitationStatus effectiveStatus = invitation.getStatus();
        if (effectiveStatus == InvitationStatus.PENDING && invitation.getExpirationDate().isBefore(LocalDateTime.now())) {
            effectiveStatus = InvitationStatus.EXPIRED;
        }
        User invitedBy = invitation.getInvitedBy();
        String invitedByName = invitedBy == null ? null : invitedBy.getNom() + " " + invitedBy.getPrenom();
        return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole(),
                effectiveStatus, invitation.getExpirationDate(), invitation.getCreatedAt(), invitedByName);
    }

    // CSS inline uniquement + tables role="presentation" : contraintes de compatibilité email,
    private String buildInvitationEmailHtml(User admin, Role role, String activationLink) {
        String roleLabel = roleLabel(role);
        int currentYear = Year.now().getValue();
        return """
                <!DOCTYPE html>
                <html lang="fr" xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Invitation à rejoindre MedTrack</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f4f5f7;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f5f7; width:100%%;">
                    <tr>
                        <td align="center" style="padding:40px 16px;">
                            <!-- Carte blanche centrée (600px max) -->
                            <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:600px; max-width:600px; background-color:#ffffff; border:1px solid #e3e6ea; border-radius:12px;">
                                <!-- En-tête : nom MedTrack en évidence -->
                                <tr>
                                    <td align="center" style="padding:32px 40px 12px 40px; font-family:Arial, Helvetica, sans-serif; font-size:26px; font-weight:bold; color:#1a1f36; letter-spacing:-0.5px;">
                                        Med<span style="color:#2563eb;">Track</span>
                                    </td>
                                </tr>
                                <!-- Titre clair -->
                                <tr>
                                    <td align="center" style="padding:4px 40px 0 40px;">
                                        <h1 style="margin:0; font-family:Arial, Helvetica, sans-serif; font-size:22px; font-weight:bold; color:#1a1f36; line-height:1.3;">Vous avez été invité(e) à rejoindre MedTrack</h1>
                                    </td>
                                </tr>
                                <!-- Texte d'explication : qui invite, en tant que quel rôle -->
                                <tr>
                                    <td align="center" style="padding:18px 40px 0 40px;">
                                        <p style="margin:0; font-family:Arial, Helvetica, sans-serif; font-size:15px; color:#5b6573; line-height:1.6;">
                                            <strong style="color:#1a1f36;">%s %s</strong> vous invite à rejoindre MedTrack en tant que <strong style="color:#1a1f36;">%s</strong>.
                                        </p>
                                    </td>
                                </tr>
                                <!-- Vrai bouton cliquable stylé (fond bleu, texte blanc, coins arrondis) -->
                                <tr>
                                    <td align="center" style="padding:28px 40px 8px 40px;">
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                                            <tr>
                                                <td align="center" style="border-radius:8px; background-color:#2563eb;">
                                                    <a href="%s" target="_blank" style="display:inline-block; padding:14px 34px; font-family:Arial, Helvetica, sans-serif; font-size:15px; font-weight:bold; color:#ffffff; text-decoration:none; border-radius:8px;">Activer mon compte</a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <!-- Mention d'expiration, texte plus discret/gris -->
                                <tr>
                                    <td align="center" style="padding:10px 40px 0 40px;">
                                        <p style="margin:0; font-family:Arial, Helvetica, sans-serif; font-size:13px; color:#8a94a6; line-height:1.5;">Ce lien d'activation est valable 48 heures.</p>
                                    </td>
                                </tr>
                                <!-- Footer discret -->
                                <tr>
                                    <td style="padding:28px 40px; border-top:1px solid #eef0f3;">
                                        <p style="margin:0 0 8px 0; font-family:Arial, Helvetica, sans-serif; font-size:12px; color:#8a94a6; line-height:1.5;">Si vous n'êtes pas à l'origine de cette demande, vous pouvez ignorer cet email en toute sécurité.</p>
                                        <p style="margin:0; font-family:Arial, Helvetica, sans-serif; font-size:12px; color:#8a94a6; line-height:1.5;">© %d MedTrack. Tous droits réservés.</p>
                                    </td>
                                </tr>
                            </table>
                            <!-- Fin de la carte -->
                        </td>
                    </tr>
                </table>
                </body>
                </html>
                """.formatted(admin.getPrenom(), admin.getNom(), roleLabel, activationLink, currentYear);
    }

    private String roleLabel(Role role) {
        return switch (role) {
            case ADMIN -> "administrateur";
            case MEDECIN -> "médecin";
        };
    }

    /**
     * 404 si introuvable, 410 si expirée, 409 si déjà utilisée. L'expiration est vérifiée avant
     * le statut (et pas l'inverse) : une invitation PENDING dont la date est dépassée bascule ici
     * en EXPIRED, une fois pour toutes. Si le contrôle inverse était fait en premier, ce même appel
     * passerait bien (statut encore PENDING), mais le PROCHAIN appel sur ce token — un simple
     * rechargement de page, ou l'appel POST /activate qui suit le GET de vérification — tomberait
     * sur le statut EXPIRED déjà persisté et renverrait "déjà utilisée" au lieu de "expirée" :
     * message faux, sans lien avec la vraie cause. Ordonner ainsi rend le résultat stable, qu'on
     * soit au premier appel après expiration ou au dixième.
     */
    private Invitation validatePendingInvitation(String token) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation introuvable"));

        if (invitation.getStatus() == InvitationStatus.PENDING && invitation.getExpirationDate().isBefore(LocalDateTime.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
        }

        if (invitation.getStatus() == InvitationStatus.EXPIRED || invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new ResponseStatusException(HttpStatus.GONE, "Cette invitation n'est plus valide");
        }

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette invitation a déjà été utilisée");
        }

        return invitation;
    }

    /**
     * Vérifie un token et renvoie l'email associé (pour pré-remplir l'UI frontend).
     * Endpoint public : aucune authentification requise.
     * La mise à EXPIRED (cas expiré) est persistée car cette méthode n'est pas transactionnelle.
     */
    public String getInvitationEmail(String token) {
        return validatePendingInvitation(token).getEmail();
    }

    /**
     * Active un compte à partir d'une invitation : crée le User (mot de passe BCrypt),
     * renseigne invitedBy et marque l'invitation ACCEPTED.
     * Transactionnel : si la création du User échoue, l'invitation reste PENDING.
     * Endpoint public : aucune authentification requise.
     */
    @Transactional
    public UserResponse activateInvitation(String token, ActivateRequest request) {
        // Revalide le token indépendamment d'un éventuel appel préalable au GET
        Invitation invitation = validatePendingInvitation(token);

        if (userRepository.findByEmail(EmailNormalizer.normalize(invitation.getEmail())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email est déjà utilisé");
        }

        User user = new User();
        user.setEmail(invitation.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setRole(invitation.getRole());
        user.setInvitedBy(invitation.getInvitedBy());
        user.setActif(true);
        user.setSpecialite(request.getSpecialite());
        user.setNumeroOrdre(request.getNumeroOrdre());
        user.setTelephone(request.getTelephone());
        User saved = userRepository.save(user);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        return userMapper.toResponse(saved);
    }
}
