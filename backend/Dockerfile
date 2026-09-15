# Backend image, built from the repository root:
#
#   docker build -f backend/Dockerfile -t invoicing-backend .
#
# Two stages. The first has a JDK and Maven and produces the jar; the second has
# only a JRE and the jar. Nothing from the build stage — no sources, no Maven
# repository, no compiler — ends up in the image that runs in production.

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# The wrapper and the pom first, on their own. As long as the dependencies do
# not change, Docker reuses the cached download layer and a code change rebuilds
# only the last step instead of fetching the internet again.
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
# the executable bit does not survive a checkout on every platform
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY backend/src/ src/

# Tests are deliberately not run here. They are integration tests on
# Testcontainers, which needs a Docker daemon this build does not have and
# should not be given. `./mvnw test` on the host and the CI pipeline are where
# they run; this stage only packages what those already vouched for.
RUN ./mvnw -B -ntp clean package -DskipTests

# ---------------------------------------------------------------------------
# Runtime
# ---------------------------------------------------------------------------
# Alpine, which is roughly a third of the size of the Ubuntu-based JRE. That is
# only safe because the PDF font is embedded in the jar: an image with no system
# fonts and no fontconfig would otherwise be exactly where invoice rendering
# breaks. It is verified rather than assumed — see the report for the euro sign
# and the embedded-font check run against this image.
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is here only so the container can answer HEALTHCHECK; it costs about a
# megabyte and saves starting a JVM just to make one HTTP request.
RUN apk add --no-cache curl

# A dedicated unprivileged account. A process that never needs to write outside
# its own directory has no business being able to.
RUN addgroup -S -g 1001 app \
    && adduser -S -u 1001 -G app -H app

WORKDIR /app
COPY --from=build --chown=app:app /build/target/*.jar app.jar
USER app

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=5 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then sizes its heap from
# whatever the container is actually given, instead of a number that is wrong on
# every machine but the one it was written for.
#
# headless because nothing here draws on a screen. The PDF font is embedded in
# the jar, so the renderer asks the operating system for nothing at all — which
# is what makes an invoice from this image identical to one from a laptop.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.awt.headless=true", \
    "-jar", "/app/app.jar"]
