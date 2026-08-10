package tn.coconsult.medtrack.ai.dto;

import java.util.List;

public record PrescriptionSummaryPage(List<PrescriptionSummaryResponse> content) {

    public List<PrescriptionSummaryResponse> safeContent() {
        return content == null ? List.of() : content;
    }
}
