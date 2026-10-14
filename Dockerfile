# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Optional local CA for development machines whose trusted proxy inspects HTTPS.
# The secret is mounted only while building; the runtime starts from a clean image.
RUN --mount=type=secret,id=local_ca \
    set -eu; \
    if [ -s /run/secrets/local_ca ]; then \
        install -m 0644 /run/secrets/local_ca /usr/local/share/ca-certificates/local-build-ca.crt; \
        update-ca-certificates; \
        if ! keytool -list -alias local-build-ca -cacerts -storepass changeit >/dev/null 2>&1; then \
            keytool -importcert -noprompt -trustcacerts -alias local-build-ca \
                -file /run/secrets/local_ca -cacerts -storepass changeit; \
        fi; \
    fi; \
    sh ./mvnw -B -DskipTests dependency:go-offline

COPY src/main/ src/main/
RUN --mount=type=secret,id=local_ca \
    set -eu; \
    if [ -s /run/secrets/local_ca ]; then \
        install -m 0644 /run/secrets/local_ca /usr/local/share/ca-certificates/local-build-ca.crt; \
        update-ca-certificates; \
        if ! keytool -list -alias local-build-ca -cacerts -storepass changeit >/dev/null 2>&1; then \
            keytool -importcert -noprompt -trustcacerts -alias local-build-ca \
                -file /run/secrets/local_ca -cacerts -storepass changeit; \
        fi; \
    fi; \
    sh ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app \
    && mkdir /app \
    && chown app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/estudio-mercado-0.1.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:InitialRAMPercentage=15", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
