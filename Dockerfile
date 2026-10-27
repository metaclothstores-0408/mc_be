# ==========================================
# 1. Build Stage
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and compile
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# 2. Production Runtime Stage
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy packaged jar from builder
COPY --from=build /app/target/*.jar app.jar

# Render assigns a dynamic port via the $PORT env variable
EXPOSE 8080
ENV PORT=8080

# -XX:MaxRAMPercentage=75.0 ensures JVM doesn't exceed Render free/starter memory limits (512MB)
# -Dserver.port=${PORT:-8080} dynamically binds to Render's port
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT:-8080} -jar app.jar"]

