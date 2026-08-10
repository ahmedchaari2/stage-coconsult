package tn.coconsult.medtrack.dashboard.dto;

import java.time.LocalDate;

public record PrescriptionDto(
        Long id,
        String medicament,
        Integer dureeJours,
        boolean archived,
        LocalDate consultationDate) {
}
