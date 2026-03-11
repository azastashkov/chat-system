FROM gradle:8-jdk21 AS builder
WORKDIR /app
COPY . .
ARG MODULE
RUN ./gradlew :${MODULE}:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG MODULE
COPY --from=builder /app/${MODULE}/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
