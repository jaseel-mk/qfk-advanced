FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY backend ./backend
RUN cd backend && mvn -q package

FROM eclipse-temurin:21-jre
RUN useradd -r qfk
USER qfk
COPY --from=build /source/backend/target/*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
