# ---------- Build Stage ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# pom.xml copy karo
COPY pom.xml .
RUN mvn dependency:go-offline

# source code copy karo
COPY src ./src

# build jar (tests skip)
RUN mvn clean package -DskipTests


# ---------- Run Stage ----------
FROM eclipse-temurin:17-jdk

WORKDIR /app

# build stage se jar copy karo
COPY --from=build /app/target/*.jar app.jar

# Render uses PORT env variable
EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
