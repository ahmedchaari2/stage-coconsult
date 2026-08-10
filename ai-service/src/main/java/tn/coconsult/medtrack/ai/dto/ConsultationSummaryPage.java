package tn.coconsult.medtrack.ai.dto;

import java.util.List;

public record ConsultationSummaryPage(List<ConsultationSummaryResponse> content) {

    public List<ConsultationSummaryResponse> safeContent() {
        return content == null ? List.of() : content;
    }
}
