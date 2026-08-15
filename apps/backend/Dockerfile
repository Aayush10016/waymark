# syntax=docker/dockerfile:1.7

FROM maven:3.9.16-eclipse-temurin-17@sha256:1ed5d1f54416b706707b4f3238f63a20bb06aab27c6d240090a2bb9ad895ed45 AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY apps/backend/pom.xml apps/backend/pom.xml
COPY apps/backend/src apps/backend/src

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/* \
    && sed -i 's/\r$//' mvnw \
    && chmod +x mvnw \
    && ./mvnw -pl apps/backend -am -DskipTests package

FROM eclipse-temurin:17.0.17_10-jre-noble@sha256:3421d7f073ded5491600707946dd7593502bdc87a18b3f234e7069574fb80401
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 trail \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin trail

COPY --from=build --chown=10001:10001 /workspace/apps/backend/target/trail-backend-*.jar /app/trail-backend.jar

USER 10001:10001
EXPOSE 8080
STOPSIGNAL SIGTERM

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:${PORT:-8080}/actuator/health/readiness >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar /app/trail-backend.jar"]
