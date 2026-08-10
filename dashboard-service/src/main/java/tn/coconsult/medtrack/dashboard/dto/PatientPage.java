package tn.coconsult.medtrack.dashboard.dto;

import java.util.List;

public record PatientPage(List<PatientDto> content, long totalElements) {

    public List<PatientDto> safeContent() {
        return content == null ? List.of() : content;
    }
}
