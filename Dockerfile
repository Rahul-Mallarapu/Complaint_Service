FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml .
RUN mvn -P prod dependency:go-offline -B
COPY src ./src
COPY .mvn ./.mvn
RUN mvn -P prod -DskipTests clean package
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/demo.smart_complaint_management_system-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]