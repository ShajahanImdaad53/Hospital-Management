# ==========================================
# Multi-Stage Dockerfile for NexusHealth Clinic
# ==========================================

# Stage 1: Build stage with OpenJDK 22 and Maven
FROM eclipse-temurin:22-jdk-jammy AS build
WORKDIR /app

# Copy Maven wrapper and POM first for efficient layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Convert CRLF line endings to LF and make wrapper executable
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build production runnable JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:22-jre-jammy
WORKDIR /app

# Copy built JAR from previous stage
COPY --from=build /app/target/*.jar app.jar

# Cloud providers (Render, Koyeb, Cloud Run) inject PORT dynamically
ENV PORT=8081
EXPOSE 8081

# JVM flags optimized for free cloud containers (512MB RAM)
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xmx350m", "-Xss512k", "-jar", "app.jar"]
