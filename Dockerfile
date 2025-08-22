# Stage 1: Build
FROM maven:3.8.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy Maven config and source
COPY pom.xml .
COPY src ./src

# Build the JAR using Maven wrapper
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy only the final JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
