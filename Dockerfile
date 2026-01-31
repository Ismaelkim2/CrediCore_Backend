# ---------- Build stage ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar
COPY --from=build /app/target/credit-management-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Start Spring Boot app with env variables
ENTRYPOINT ["sh", "-c", "java -Xmx400m -Xms256m -jar /app/app.jar --server.address=${SERVER_ADDRESS} --server.port=${SERVER_PORT}"]
