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
RUN cat <<'EOF' > /app/start.sh
#!/bin/sh
EXTRA=""
# Prefer explicit Spring datasource variables when they are already provided.
if [ -n "$SPRING_DATASOURCE_URL" ]; then
  EXTRA="$EXTRA -Dspring.datasource.url=$SPRING_DATASOURCE_URL"
  if [ -n "$SPRING_DATASOURCE_USERNAME" ]; then
    EXTRA="$EXTRA -Dspring.datasource.username=$SPRING_DATASOURCE_USERNAME"
  fi
  if [ -n "$SPRING_DATASOURCE_PASSWORD" ]; then
    EXTRA="$EXTRA -Dspring.datasource.password=$SPRING_DATASOURCE_PASSWORD"
  fi
elif [ -n "$DATABASE_URL" ]; then
  # Parse postgres:// or postgresql://user:pass@host:port/db safely.
  STRIPPED=${DATABASE_URL#postgres://}
  STRIPPED=${STRIPPED#postgresql://}
  USERINFO=${STRIPPED%@*}
  HOSTPART=${STRIPPED##*@}
  DB_USER=${USERINFO%%:*}
  DB_PASS=${USERINFO#*:}
  case "$HOSTPART" in
    *\?*) JDBC_URL="jdbc:postgresql://$HOSTPART&prepareThreshold=0" ;;
    *) JDBC_URL="jdbc:postgresql://$HOSTPART?prepareThreshold=0" ;;
  esac
  EXTRA="$EXTRA -Dspring.datasource.url=$JDBC_URL"
  EXTRA="$EXTRA -Dspring.datasource.username=$DB_USER"
  EXTRA="$EXTRA -Dspring.datasource.password=$DB_PASS"
fi
# Redis URL: only inject when set (empty string crashes Spring Boot)
if [ -n "$SPRING_DATA_REDIS_URL" ]; then
  EXTRA="$EXTRA -Dspring.data.redis.url=$SPRING_DATA_REDIS_URL"
elif [ -n "$REDIS_URL" ]; then
  EXTRA="$EXTRA -Dspring.data.redis.url=$REDIS_URL"
fi
# CloudAMQP URL: only inject when set
if [ -n "$SPRING_RABBITMQ_ADDRESSES" ]; then
  EXTRA="$EXTRA -Dspring.rabbitmq.addresses=$SPRING_RABBITMQ_ADDRESSES"
elif [ -n "$CLOUDAMQP_URL" ]; then
  EXTRA="$EXTRA -Dspring.rabbitmq.addresses=$CLOUDAMQP_URL"
fi
echo "Starting backend on port ${PORT:-8080}..."
exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} $EXTRA -jar app.jar
EOF
RUN chmod +x /app/start.sh

ENTRYPOINT ["/app/start.sh"]
