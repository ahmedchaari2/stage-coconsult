package tn.coconsult.medtrack.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.user.model.PasswordResetToken;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.PasswordResetTokenRepository;
import tn.coconsult.medtrack.user.repository.UserRepository;
import tn.coconsult.medtrack.user.util.EmailNormalizer;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.Optional;

/**
 * Service métier du flux "mot de passe oublié" : génération/envoi du token (public,
 * ne révèle jamais l'existence d'un compte) et réinitialisation effective du mot de passe.
 * Même style que {@link InvitationService} (token opaque + email HTML inline).
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthService authService;

    @Value("${medtrack.frontend.url}")
    private String frontendUrl;

    /**
     * Envoie l'email de réinitialisation si un compte actif existe pour cet email. Ne lève
     * jamais d'exception et ne distingue pas "compte inexistant" de "compte existant" côté
     * appelant, pour ne pas révéler l'existence d'un compte via cet endpoint.
     * <p>
     * Un échec d'envoi (SMTP down, etc.) est journalisé mais n'empêche pas l'appelant de
     * répondre normalement : annuler la création du token créerait un écart observable entre
     * "email inexistant" et "email existant mais SMTP en panne". Le token orphelin est sans
     * risque, il expire après 1h et est purgé par {@link PasswordResetTokenCleanupService}.
     */
    public void forgotPassword(String email) {
        Optional<User> userOpt = userRepository.findByEmail(EmailNormalizer.normalize(email));
        if (userOpt.isEmpty() || Boolean.FALSE.equals(userOpt.get().getActif())) {
            return;
        }
        User user = userOpt.get();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.getId());
        resetToken = passwordResetTokenRepository.save(resetToken);

        String resetLink = frontendUrl + "/reset-password/" + resetToken.getToken();
        String subject = "Medtrack - Réinitialisation de votre mot de passe";
        // Un échec SMTP est journalisé côté EmailService ; le token reste en base, sans risque
        // (il expire après 1h et est purgé par PasswordResetTokenCleanupService).
        emailService.sendHtmlEmail(user.getEmail(), subject, buildResetPasswordEmailHtml(user, resetLink));
    }

    /**
     * Réinitialise le mot de passe à partir d'un token valide, non expiré et non utilisé.
     * Révoque ensuite TOUS les refresh tokens de l'utilisateur (déconnexion forcée de toutes
     * les sessions actives) : un mot de passe oublié + réinitialisé doit invalider les
     * sessions ouvertes, potentiellement compromises en même temps que le mot de passe.
     *
     * @throws ResponseStatusException 400 si le token est introuvable, déjà utilisé ou expiré
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Lien de réinitialisation invalide"));

        if (resetToken.isUsed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce lien de réinitialisation a déjà été utilisé");
        }
        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce lien de réinitialisation a expiré");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Lien de réinitialisation invalide"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        authService.revokeAllForUser(user);
    }

    private String buildResetPasswordEmailHtml(User user, String resetLink) {
        int currentYear = Year.now().getValue();
        return """
                <!DOCTYPE html>
                <html lang="fr" xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Réinitialisation de votre mot de passe MedTrack</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f4f5f7;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f5f7; width:100%%;">
                    <tr>
                        <td align="center" style="padding:40px 16px;">
                            <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:600px; max-width:600px; background-color:#ffffff; border:1px solid #e3e6ea; border-radius:12px;">
                                <tr>
                                    <td align="center" style="padding:32px 40px 12px 40px; font-family:Arial, Helvetica, sans-serif; font-size:26px; font-weight:bold; color:#1a1f36; letter-spacing:-0.5px;">
                                        Med<span style="color:#2563eb;">Track</span>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:4px 40px 0 40px;">
                                        <h1 style="margin:0; font-family:Arial, Helvetica, sans-serif; font-size:22px; font-weight:bold; color:#1a1f36; line-height:1.3;">Réinitialisation de votre mot de passe</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:18px 40px 0 40px;">
                                        <p style="margin:0; font-family:Arial, Helvetica, sans-serif; font-size:15px; color:#5b6573; line-height:1.6;">
                                            Bonjour <strong style="color:#1a1f36;">%s %s</strong>, vous avez demandé la réinitialisation du mot de passe associé à ce compte MedTrack.
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:28px 40px 8px 40px;">
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                                            <tr>
                                                <td align="center" style="border-radius:8px; background-color:#2563eb;">
                                                    <a href="%s" target="_blank" style="display:inline-block; padding:14px 34px; font-family:Arial, Helvetica, sans-serif; font-size:15px; font-weight:bold; color:#ffffff; text-decoration:none; border-radius:8px;">Réinitialiser mon mot de passe</a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:10px 40px 0 40px;">
                                        <p style="margin:0; font-family:Arial, Helvetica, sans-serif; font-size:13px; color:#8a94a6; line-height:1.5;">Ce lien est valable 1 heure.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:28px 40px; border-top:1px solid #eef0f3;">
                                        <p style="margin:0 0 8px 0; font-family:Arial, Helvetica, sans-serif; font-size:12px; color:#8a94a6; line-height:1.5;">Si vous n'êtes pas à l'origine de cette demande, vous pouvez ignorer cet email en toute sécurité : votre mot de passe restera inchangé.</p>
                                        <p style="margin:0; font-family:Arial, Helvetica, sans-serif; font-size:12px; color:#8a94a6; line-height:1.5;">© %d MedTrack. Tous droits réservés.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                </body>
                </html>
                """.formatted(user.getPrenom(), user.getNom(), resetLink, currentYear);
    }
}
