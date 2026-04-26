package tech.wenisch.kairos.service;

import tech.wenisch.kairos.entity.CheckStatus;

import java.util.Locale;

final class CheckFailureClassifier {

    private CheckFailureClassifier() {
    }

    static CheckStatus resolveStatus(Throwable error) {
        return isDatabaseInfrastructureFailure(error)
                ? CheckStatus.UNKNOWN
                : CheckStatus.NOT_AVAILABLE;
    }

    static boolean isDatabaseInfrastructureFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toUpperCase(Locale.ROOT);
                if (normalized.contains("JDBC")
                        || normalized.contains("JPA")
                        || normalized.contains("ENTITYMANAGER")
                        || normalized.contains("SQLTRANSIENTCONNECTIONEXCEPTION")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}