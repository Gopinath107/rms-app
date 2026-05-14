# Stage 1: Build the application
# Change here: Updated to Maven 3.9 with Eclipse Temurin JDK 21 (Available and Stable)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Create the final, smaller image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/rms-app-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
