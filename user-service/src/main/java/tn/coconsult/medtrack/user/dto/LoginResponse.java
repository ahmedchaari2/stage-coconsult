package tn.coconsult.medtrack.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.user.model.Role;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Long id;

    private String email;
    private String nom;
    private String prenom;
    private Role role;
    private Boolean actif;
    private String photoUrl;
    private String specialite;
    private String numeroOrdre;
    private String telephone;
    private LocalDateTime createdAt;
}
