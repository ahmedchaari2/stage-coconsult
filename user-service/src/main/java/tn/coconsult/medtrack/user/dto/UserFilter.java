package tn.coconsult.medtrack.user.dto;

public record UserFilter(
        String nom,
        String prenom,
        String email,
        String specialite,
        String numeroOrdre,
        Boolean actif,
        String q,
        Boolean sansSpecialite,
        Boolean sansNumeroOrdre
) {
}
