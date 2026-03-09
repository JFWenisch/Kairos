package com.jfwendisch.kairos.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.jfwendisch.kairos.entity.CheckResult;
import com.jfwendisch.kairos.entity.CheckStatus;
import com.jfwendisch.kairos.entity.MonitoredResource;
import com.jfwendisch.kairos.repository.CheckResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DockerCheckService {

    private final CheckResultRepository checkResultRepository;

    public CheckResult check(MonitoredResource resource) {
        String image = resource.getTarget();
        DockerClient dockerClient = null;
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(5)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
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
}
