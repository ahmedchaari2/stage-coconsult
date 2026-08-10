package tn.coconsult.medtrack.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.user.model.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    @Email(message = "Format d'email invalide")
    private String email;

    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    private String nom;

    private String prenom;

    private Role role;

    private Boolean actif;

    private String specialite;

    private String numeroOrdre;

    @Size(max = 30, message = "Le numéro de téléphone ne doit pas dépasser 30 caractères")
    @Pattern(regexp = "^[+()0-9 .-]*$", message = "Format de téléphone invalide")
    private String telephone;
}
