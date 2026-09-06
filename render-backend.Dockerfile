FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY backend-deploy.tar.gz .
RUN tar -xzf backend-deploy.tar.gz && cd backend && mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd -r qfk
USER qfk
COPY --from=build /source/backend/target/*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
