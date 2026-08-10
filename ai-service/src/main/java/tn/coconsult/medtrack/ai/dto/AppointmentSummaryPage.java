package tn.coconsult.medtrack.ai.dto;

import java.util.List;

/** Concrete page type; Feign/Jackson serialize it more reliably than a generic PageDTO<T>. */
public record AppointmentSummaryPage(List<AppointmentSummaryResponse> content) {

    public List<AppointmentSummaryResponse> safeContent() {
        return content == null ? List.of() : content;
    }
}
