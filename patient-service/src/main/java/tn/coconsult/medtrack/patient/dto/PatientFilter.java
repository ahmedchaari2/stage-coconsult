package tn.coconsult.medtrack.patient.dto;

import java.time.LocalDate;

public record PatientFilter(
        String nom,
        String prenom,
        String email,
        Long medecinReferentId,
        String telephone,
        String numeroDossier,
        LocalDate dateNaissanceFrom,
        LocalDate dateNaissanceTo,
        // Non fourni par le client en pratique : ADMIN peut demander les archivés, MEDECIN reste toujours restreint aux actifs.
        Boolean archived,
        String q,
        Boolean sansNumeroDossier,
        Boolean sansCin
) {
}
