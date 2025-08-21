# Stage 1: Build
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

# Copy Maven config and source
COPY pom.xml .
COPY src ./src

# Build the JAR
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy only the final JAR from builder
COPY --from=builder /app/target/setup-app.jar app.jar

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
