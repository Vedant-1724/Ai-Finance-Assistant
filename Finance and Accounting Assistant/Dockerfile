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

# Startup script: conditionally inject cloud service URLs only when set
RUN printf '#!/bin/sh\n\
EXTRA=""\n\
# Database URL: auto-prepend jdbc: if needed\n\
if [ -n "$DATABASE_URL" ]; then\n\
  JDBC_URL=$(echo "$DATABASE_URL" | sed "s|^postgresql://|jdbc:postgresql://|")\n\
  EXTRA="$EXTRA -Dspring.datasource.url=$JDBC_URL"\n\
fi\n\
# Redis URL: only inject when set (empty string crashes Spring Boot)\n\
if [ -n "$REDIS_URL" ]; then\n\
  EXTRA="$EXTRA -Dspring.data.redis.url=$REDIS_URL"\n\
fi\n\
# CloudAMQP URL: only inject when set\n\
if [ -n "$CLOUDAMQP_URL" ]; then\n\
  EXTRA="$EXTRA -Dspring.rabbitmq.addresses=$CLOUDAMQP_URL"\n\
fi\n\
echo "Starting backend on port ${PORT:-8080}..."\n\
exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} $EXTRA -jar app.jar\n\
' > /app/start.sh && chmod +x /app/start.sh

ENTRYPOINT ["/app/start.sh"]