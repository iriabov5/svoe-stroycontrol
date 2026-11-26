FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
RUN --mount=type=cache,target=/root/.gradle chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --no-create-home app
WORKDIR /app
COPY --from=build /src/build/libs/svoe-stroycontrol-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
