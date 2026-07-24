# syntax=docker/dockerfile:1
#
# notification-service — multi-stage image (SPEC §Session 6).
# Build from the reactor root so the whole multi-module build is in context:
#   docker build -f notification-service/Dockerfile -t notification-service .

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
    && ./mvnw -B -ntp -pl notification-service dependency:go-offline

COPY notification-service/src/ notification-service/src/
# Tests run in CI against the full reactor (ci.yml), not during the image build.
RUN ./mvnw -B -ntp -pl notification-service -DskipTests package

# Split the fat jar into its layers so Docker can cache the parts that rarely
# change (dependencies) separately from the part that changes every commit.
RUN java -Djarmode=tools -jar notification-service/target/notification-service-*.jar \
    extract --layers --launcher --destination /build/extracted

# ---------- Stage 2: runtime (JRE) ----------
FROM eclipse-temurin:25-jre AS runtime

# This service speaks gRPC only — there is no HTTP endpoint to curl, so the
# standard grpc.health.v1.Health service (registered by spring-grpc) is probed
# instead. TARGETARCH is set by BuildKit, so this resolves per build platform.
ARG TARGETARCH
ARG GRPC_HEALTH_PROBE_VERSION=v0.4.53
ADD https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/${GRPC_HEALTH_PROBE_VERSION}/grpc_health_probe-linux-${TARGETARCH} /usr/local/bin/grpc_health_probe

RUN chmod 0755 /usr/local/bin/grpc_health_probe \
    && useradd --system --uid 10001 --user-group --no-create-home --shell /usr/sbin/nologin taskhub

WORKDIR /app
COPY --from=build --chown=taskhub:taskhub /build/extracted/dependencies/ ./
COPY --from=build --chown=taskhub:taskhub /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=taskhub:taskhub /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=taskhub:taskhub /build/extracted/application/ ./

USER taskhub
EXPOSE 9090

# Empty service name = overall server health; per-service names are not registered.
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
    CMD grpc_health_probe -addr=127.0.0.1:9090 || exit 1

# MaxRAMPercentage keeps the heap inside the container limit rather than the host's RAM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
