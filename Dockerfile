# syntax=docker/dockerfile:1.7

FROM gcr.io/distroless/java17-debian12:nonroot
WORKDIR /app

COPY target/kairos-*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
