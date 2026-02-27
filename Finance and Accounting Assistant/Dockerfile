# ─── Stage 1: Build JAR with Maven ───────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy pom.xml first so Maven dependency layer is cached
# (only re-downloads dependencies when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B --quiet

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B --quiet

# ─── Stage 2: Minimal JRE runtime image ──────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
