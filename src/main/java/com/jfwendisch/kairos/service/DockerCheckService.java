package com.jfwendisch.kairos.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.jfwendisch.kairos.entity.CheckResult;
import com.jfwendisch.kairos.entity.CheckStatus;
import com.jfwendisch.kairos.entity.MonitoredResource;
import com.jfwendisch.kairos.entity.ResourceTypeAuth;
import com.jfwendisch.kairos.repository.CheckResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DockerCheckService {

    private final CheckResultRepository checkResultRepository;
    private final AuthService authService;

    public CheckResult check(MonitoredResource resource) {
        String image = resource.getTarget();
        DockerClient dockerClient = null;
        try {
            // Attempt docker login if a matching credential is configured for this image target
            Optional<ResourceTypeAuth> authOpt = authService.findMatchingAuth(image, "DOCKER");
            if (authOpt.isPresent()) {
                ResourceTypeAuth auth = authOpt.get();
                log.debug("Applying Basic Auth '{}' (docker login) for Docker check on {}", auth.getName(), image);
                performDockerLogin(extractRegistry(image), auth.getUsername(), auth.getPassword());
            }

            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(5)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofMinutes(3)) // allow time for large image pulls
                    .build();
            dockerClient = DockerClientImpl.getInstance(config, httpClient);

            String imageRepo = image;
            String imageTag = "latest";
            if (image.contains(":")) {
                int colonIdx = image.lastIndexOf(':');
                imageRepo = image.substring(0, colonIdx);
                imageTag = image.substring(colonIdx + 1);
            }

            dockerClient.pullImageCmd(imageRepo)
                    .withTag(imageTag)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion(2, TimeUnit.MINUTES);

            try {
                String imageId = imageRepo + ":" + imageTag;
                dockerClient.removeImageCmd(imageId).withForce(true).exec();
            } catch (Exception removeEx) {
                log.warn("Could not remove docker image '{}' after check: {}", image, removeEx.getMessage());
            }

            CheckResult result = CheckResult.builder()
                    .resource(resource)
                    .status(CheckStatus.AVAILABLE)
                    .checkedAt(LocalDateTime.now())
                    .message("Image pulled successfully")
                    .build();
            return checkResultRepository.save(result);

        } catch (Exception e) {
            log.warn("Docker check failed for image {}: {}", image, e.getMessage());
            CheckResult result = CheckResult.builder()
                    .resource(resource)
                    .status(CheckStatus.NOT_AVAILABLE)
                    .checkedAt(LocalDateTime.now())
                    .message(e.getMessage())
                    .errorCode("DOCKER_ERROR")
                    .build();
            return checkResultRepository.save(result);
        } finally {
            if (dockerClient != null) {
                try {
                    dockerClient.close();
                } catch (Exception ex) {
                    log.debug("Error closing docker client: {}", ex.getMessage());
                }
            }
        }
    }

    /**
     * Extracts the registry hostname from a Docker image reference.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code nginx} → {@code ""} (Docker Hub, login without host)</li>
     *   <li>{@code registry.example.com/myimage:tag} → {@code registry.example.com}</li>
     *   <li>{@code ghcr.io/owner/image:tag} → {@code ghcr.io}</li>
     * </ul>
     * </p>
     */
    private String extractRegistry(String image) {
        String repoWithoutTag = image.contains(":") ? image.substring(0, image.lastIndexOf(':')) : image;
        int slashIdx = repoWithoutTag.indexOf('/');
        if (slashIdx == -1) {
            // plain image name like "nginx" — Docker Hub
            return "";
        }
        String firstSegment = repoWithoutTag.substring(0, slashIdx);
        // A registry host contains a dot or a colon (port), otherwise it's a Docker Hub namespace
        if (firstSegment.contains(".") || firstSegment.contains(":")) {
            return firstSegment;
        }
        return "";
    }

    /**
     * Executes {@code docker login} for the given registry using the provided credentials.
     * The password is supplied via stdin to avoid it appearing in the process argument list.
     *
     * @param registry the registry hostname, or empty string for Docker Hub
     * @param username the registry username
     * @param password the registry password or token
     * @throws IOException          if the process cannot be started
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws RuntimeException     if the login command exits with a non-zero code
     */
    private void performDockerLogin(String registry, String username, String password)
            throws IOException, InterruptedException {
        ProcessBuilder pb;
        if (registry == null || registry.isBlank()) {
            pb = new ProcessBuilder("docker", "login", "--username", username, "--password-stdin");
        } else {
            pb = new ProcessBuilder("docker", "login", registry, "--username", username, "--password-stdin");
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(password.getBytes());
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new RuntimeException("docker login failed (exit " + exitCode + "): " + output);
        }
        log.info("docker login succeeded for registry '{}'", registry.isBlank() ? "docker.io" : registry);
    }
}
