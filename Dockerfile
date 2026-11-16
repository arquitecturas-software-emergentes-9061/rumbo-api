# Build stage: compiles and packages the API with the same JDK used in CI.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn -B --no-transfer-progress package -DskipTests

# Runtime stage: only the JRE and the executable jar, running as a non-root user.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S rumbo && adduser -S rumbo -G rumbo
COPY --from=build /workspace/target/rumbo-api-*.jar app.jar
USER rumbo
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
