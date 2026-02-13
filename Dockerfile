# -------------------------------------------------------
# FASE 1: BUILD
# -------------------------------------------------------
FROM gradle:8.8-jdk21 AS build
WORKDIR /app

# Copiar todo el proyecto correctamente
COPY . .

# Dar permisos al wrapper y compilar
RUN chmod +x gradlew
RUN ./gradlew clean bootJar --no-daemon

# -------------------------------------------------------
# FASE 2: RUNTIME
# -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
