package tech.wenisch.kairos.service;

import tech.wenisch.kairos.dto.InstantCheckExecutionResult;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.repository.CheckResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;

/**
 * Checks TCP reachability of a host:port target.
 * The target format is {@code host:port}, e.g. {@code mydb.example.com:5432}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TcpCheckService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private final CheckResultRepository checkResultRepository;
    private final ResourceStatusStreamService resourceStatusStreamService;
    private final OutageService outageService;

    public InstantCheckExecutionResult probe(String target, boolean skipTls, boolean useStoredAuth) {
        long checkStartedNanos = System.nanoTime();
        try {
            HostPort hp = parse(target);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(hp.host(), hp.port()), CONNECT_TIMEOUT_MS);
            }
            return InstantCheckExecutionResult.builder()
                    .status(CheckStatus.AVAILABLE)
                    .message("TCP connection to " + target + " succeeded")
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .build();
        } catch (IllegalArgumentException e) {
            return InstantCheckExecutionResult.builder()
                    .status(CheckStatus.NOT_AVAILABLE)
                    .message("Invalid target format – expected host:port, got: " + target)
                    .errorCode("INVALID_TARGET")
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .build();
        } catch (Exception e) {
            return InstantCheckExecutionResult.builder()
                    .status(CheckFailureClassifier.resolveStatus(e))
                    .message(e.getMessage())
                    .errorCode("CONNECTION_ERROR")
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .build();
        }
    }

    public CheckResult check(MonitoredResource resource) {
        String target = resource.getTarget();
        long checkStartedNanos = System.nanoTime();
        try {
            HostPort hp = parse(target);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(hp.host(), hp.port()), CONNECT_TIMEOUT_MS);
            }
            CheckResult result = CheckResult.builder()
                    .resource(resource)
                    .status(CheckStatus.AVAILABLE)
                    .checkedAt(LocalDateTime.now())
                    .message("TCP connection to " + target + " succeeded")
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .build();
            CheckResult saved = checkResultRepository.save(result);
            outageService.evaluate(resource);
            resourceStatusStreamService.publishResourceUpdate(resource);
            return saved;
        } catch (Exception e) {
            log.warn("TCP check failed for {}: {}", target, e.getMessage());
            CheckResult result = CheckResult.builder()
                    .resource(resource)
                    .status(CheckFailureClassifier.resolveStatus(e))
                    .checkedAt(LocalDateTime.now())
                    .message(e.getMessage())
                    .errorCode("CONNECTION_ERROR")
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .build();
            CheckResult saved = checkResultRepository.save(result);
            outageService.evaluate(resource);
            resourceStatusStreamService.publishResourceUpdate(resource);
            return saved;
        }
    }

    private HostPort parse(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Target must not be blank");
        }
        int lastColon = target.lastIndexOf(':');
        if (lastColon <= 0 || lastColon == target.length() - 1) {
            throw new IllegalArgumentException("Expected host:port, got: " + target);
        }
        String host = target.substring(0, lastColon);
        int port;
        try {
            port = Integer.parseInt(target.substring(lastColon + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port in target: " + target, e);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port out of range in target: " + target);
        }
        return new HostPort(host, port);
    }

    private Long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private record HostPort(String host, int port) {}
}
