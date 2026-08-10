package tn.coconsult.medtrack.dashboard.dto;

import java.util.List;

public record PrescriptionPage(List<PrescriptionDto> content, long totalElements) {

    public List<PrescriptionDto> safeContent() {
        return content == null ? List.of() : content;
    }
}
