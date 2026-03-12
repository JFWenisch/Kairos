# syntax=docker/dockerfile:1.7

FROM cgr.dev/chainguard/jre:latest
WORKDIR /app

USER nonroot

# Runtime-only image: Docker/OCI checks use registry HTTPS APIs.
# No Docker daemon, Docker CLI, or Podman tooling is included.
ENV DOCKER_HOST=unix:///dev/null
ENV CONTAINERS_CONF=/dev/null

COPY target/kairos-*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
