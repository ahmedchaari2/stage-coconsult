# syntax=docker/dockerfile:1
#
# Build context must be the repository root (docker compose sets this), because
# medtrack-common is a sibling Maven module (not part of a reactor) that every
# business service depends on and must be installed into the local repo first.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY medtrack-common ./medtrack-common
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f medtrack-common/pom.xml -DskipTests install

COPY medicalrecord-service/pom.xml medicalrecord-service/pom.xml
COPY medicalrecord-service/src medicalrecord-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f medicalrecord-service/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN useradd --no-create-home --shell /usr/sbin/nologin appuser
WORKDIR /app
COPY --from=build /build/medicalrecord-service/target/*.jar app.jar
USER appuser
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
