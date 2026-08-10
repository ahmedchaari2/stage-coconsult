package tn.coconsult.medtrack.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedecinOptionResponse {
    private Long id;
    private String nom;
    private String prenom;
    private String specialite;
    private String numeroOrdre;
    private Boolean actif;
}
