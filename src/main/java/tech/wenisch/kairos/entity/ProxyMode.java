package tech.wenisch.kairos.entity;

public enum ProxyMode {
    WHITELIST,
    BLACKLIST;

    public static ProxyMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return BLACKLIST;
        }
        try {
            return ProxyMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BLACKLIST;
        }
    }
}