# --- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first so they are cached between source-only changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
# Tests run in CI / locally with `mvn test`; they need no network, but are skipped here
# to keep image builds fast. Remove -DskipTests to run them during the build.
RUN mvn -q -B package -DskipTests

# --- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/target/institution-portfolio.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
