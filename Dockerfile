# ---------- Stage 1: build the jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the Maven wrapper + pom first so dependencies are cached between builds
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Then the source code. Tests run in CI (they need PostgreSQL), so skip them here.
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# ---------- Stage 2: small runtime image ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# don't run as root
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /app/target/scheduler-0.0.1-SNAPSHOT.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
