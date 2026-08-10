package tn.coconsult.medtrack.dashboard.dto;

import java.util.List;

/**
 * Page de rendez-vous renvoyée par appointment-service. Type concret (non générique) : Feign +
 * Jackson n'extraient pas un record générique paramétré, un wrapper dédié par contenu lève
 * l'ambiguïté. Les champs page/size/totalPages du JSON sont simplement ignorés (non utilisés).
 */
public record AppointmentPage(List<AppointmentDto> content, long totalElements) {

    public List<AppointmentDto> safeContent() {
        return content == null ? List.of() : content;
    }
}
