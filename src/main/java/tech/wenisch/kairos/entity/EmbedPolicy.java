package tech.wenisch.kairos.entity;

import java.util.Locale;

public enum EmbedPolicy {
    DISABLED,
    ALLOW_ALL,
    ALLOW_LIST;

    public static EmbedPolicy fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALLOW_ALL;
        }
        try {
            return EmbedPolicy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ALLOW_ALL;
        }
    }
}
