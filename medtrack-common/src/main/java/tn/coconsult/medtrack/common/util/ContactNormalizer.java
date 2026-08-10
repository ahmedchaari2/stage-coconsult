package tn.coconsult.medtrack.common.util;

import java.util.Locale;

public final class ContactNormalizer {

    private ContactNormalizer() {
    }

    public static String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeTunisianPhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("216")) {
            digits = digits.substring(3);
        }
        return "+216" + digits;
    }
}
