# syntax=docker/dockerfile:1
#
# taskhub-api — multi-stage image (SPEC §Session 6).
# Build from the reactor root so the whole multi-module build is in context:
#   docker build -f taskhub-api/Dockerfile -t taskhub-api .

# ---------- Stage 1: build (JDK) ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Wrapper, poms and the shared contract first — everything except this module's
# own sources. Installing taskhub-proto makes it resolvable from the local repo,
# which is what lets go-offline pull the rest of the tree down. The layer stays
# cached until a pom or the proto changes, so a source-only rebuild starts from
# a fully populated ~/.m2. (No cache mount on purpose: mounts are not carried by
# the GHA layer cache, an image layer is.)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY taskhub-proto/ taskhub-proto/
COPY taskhub-api/pom.xml taskhub-api/
COPY notification-service/pom.xml notification-service/
RUN ./mvnw -B -ntp -pl taskhub-proto -am -DskipTests install \
    && ./mvnw -B -ntp -pl taskhub-api dependency:go-offline

COPY taskhub-api/src/ taskhub-api/src/
# Tests run in CI against the full reactor (ci.yml), not during the image build.
RUN ./mvnw -B -ntp -pl taskhub-api -DskipTests package

# Split the fat jar into its layers so Docker can cache the parts that rarely
# change (dependencies) separately from the part that changes every commit.
RUN java -Djarmode=tools -jar taskhub-api/target/taskhub-api-*.jar \
    extract --layers --launcher --destination /build/extracted

# ---------- Stage 2: runtime (JRE) ----------
FROM eclipse-temurin:25-jre AS runtime

# curl backs the HEALTHCHECK below; no JDK, Maven or source ends up in this stage.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --user-group --no-create-home --shell /usr/sbin/nologin taskhub

WORKDIR /app
COPY --from=build --chown=taskhub:taskhub /build/extracted/dependencies/ ./
COPY --from=build --chown=taskhub:taskhub /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=taskhub:taskhub /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=taskhub:taskhub /build/extracted/application/ ./

USER taskhub
EXPOSE 8080

# Readiness (not liveness) — it turns UP only once Flyway and the datasource are ready.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

# MaxRAMPercentage keeps the heap inside the container limit rather than the host's RAM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
