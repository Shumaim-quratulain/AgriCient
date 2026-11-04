# Build stage: compile the app and package it as one runnable JAR (models are packaged inside it)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

# Run stage: a small image with only a Java runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
# run as a normal user, never as root
RUN useradd --system --create-home appuser
COPY --from=build /src/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER appuser
EXPOSE 8080
# keep the JVM inside the container's memory limit; 75 % leaves room for the native ONNX library
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 --enable-native-access=ALL-UNNAMED"
# The platform's own health check (AWS load balancer or ECS) should call GET /api/health.
# A container-level check is deliberately not added here: it would need an extra tool inside the image.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
