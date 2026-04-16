FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace/poc

COPY poc/gradlew ./
COPY poc/gradle ./gradle
COPY poc/build.gradle ./
COPY poc/settings.gradle ./
COPY poc/src ./src

RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/poc/build/libs/*.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
