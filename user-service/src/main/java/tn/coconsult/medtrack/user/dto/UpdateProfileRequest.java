package tn.coconsult.medtrack.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la mise à jour du profil de l'utilisateur CONNECTÉ (PUT /api/users/me).
 * Ne porte volontairement ni role ni actif : un utilisateur ne peut pas s'auto-attribuer
 * un rôle ou se réactiver/désactiver via cet endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    @Size(max = 30, message = "Le numéro de téléphone ne doit pas dépasser 30 caractères")
    @Pattern(regexp = "^[+()0-9 .-]*$", message = "Format de téléphone invalide")
    private String telephone;

    private String specialite;

    private String numeroOrdre;
}
