FROM gradle:8.13-jdk21 AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY build.gradle settings.gradle ./
COPY src ./src

RUN chmod +x ./gradlew \
    && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

ENV TZ=UTC

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
