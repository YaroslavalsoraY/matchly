# syntax=docker/dockerfile:1
# Многоэтапная сборка: jar собирается внутри контейнера, в итоговый образ попадает только JRE и jar.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
# Сначала только описание зависимостей: этот слой кэшируется и не пересобирается при правках кода
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home matchly \
    && mkdir -p /app/logs && chown -R matchly:matchly /app
COPY --from=build /build/target/matchly.jar matchly.jar
USER matchly
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "matchly.jar"]
