# syntax=docker/dockerfile:1.7

FROM cgr.dev/chainguard/jre:latest
WORKDIR /app

USER nonroot

COPY target/kairos-*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
