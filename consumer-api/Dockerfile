FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

COPY . .

RUN ./gradlew clean build -x test --no-daemon

FROM amazoncorretto:21

WORKDIR /app

COPY --from=builder /app/build/libs/*jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]