FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S fleetops && adduser -S fleetops -G fleetops
WORKDIR /app
COPY --from=build /build/target/fleetops-0.1.0.jar app.jar
USER fleetops
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]
