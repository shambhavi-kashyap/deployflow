# Step 1: Use a lightweight Java runtime environment
FROM eclipse-temurin:21-jre-jammy

# Step 2: Set the working directory inside the container
WORKDIR /app

# Step 3: Copy the compiled JAR file from the target folder into the container
COPY target/*.jar app.jar

# Step 4: Expose the port your Spring Boot app runs on
EXPOSE 8080

# Step 5: Execute the application
ENTRYPOINT ["java", "-jar", "app.jar"]