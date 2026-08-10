package tn.coconsult.medtrack.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.user.model.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserCreateRequest {
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;

    @NotNull(message = "Le rôle est obligatoire (ADMIN ou MEDECIN)")
    private Role role;

    private String specialite;

    private String numeroOrdre;

    @Size(max = 30, message = "Le numéro de téléphone ne doit pas dépasser 30 caractères")
    @Pattern(regexp = "^[+()0-9 .-]*$", message = "Format de téléphone invalide")
    private String telephone;
}
