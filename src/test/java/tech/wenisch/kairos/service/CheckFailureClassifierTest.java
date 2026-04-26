package tech.wenisch.kairos.service;

import org.junit.jupiter.api.Test;
import tech.wenisch.kairos.entity.CheckStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CheckFailureClassifierTest {

    @Test
    void resolveStatusReturnsUnknownForJpaMessage() {
        RuntimeException error = new RuntimeException("Could not open JPA EntityManager for transaction");

        CheckStatus status = CheckFailureClassifier.resolveStatus(error);

        assertThat(status).isEqualTo(CheckStatus.UNKNOWN);
    }

    @Test
    void resolveStatusReturnsUnknownForJdbcMessageInCause() {
        RuntimeException error = new RuntimeException("wrapper", new RuntimeException("Unable to acquire JDBC Connection"));

        CheckStatus status = CheckFailureClassifier.resolveStatus(error);

        assertThat(status).isEqualTo(CheckStatus.UNKNOWN);
    }

    @Test
    void resolveStatusReturnsNotAvailableForNetworkFailure() {
        RuntimeException error = new RuntimeException("Connection reset by peer");

        CheckStatus status = CheckFailureClassifier.resolveStatus(error);

        assertThat(status).isEqualTo(CheckStatus.NOT_AVAILABLE);
    }
}
