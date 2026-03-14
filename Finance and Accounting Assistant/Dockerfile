# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Limit Maven memory to fit in free tier builders
ENV MAVEN_OPTS="-Xmx512m -XX:MaxMetaspaceSize=256m"

# Copy Maven wrapper and pom first (better caching)
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN chmod +x mvnw

# Copy source and build (combined for stability)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B -q

# ── Stage 2: Run ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Railway uses $PORT. We also limit memory to stay within free tier limits.
ENV JAVA_OPTS="-Xmx448m -Xms256m -XX:MaxMetaspaceSize=128m"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "export JDBC_URL=$(echo \"${DATABASE_URL:-jdbc:postgresql://localhost:5432/financedb}\" | sed 's|^postgresql://|jdbc:postgresql://|'); echo \"Starting with DB URL: ${JDBC_URL%:*}:****\"; java $JAVA_OPTS -Dserver.port=${PORT:-8080} -Dspring.datasource.url=\"$JDBC_URL\" -jar app.jar"]