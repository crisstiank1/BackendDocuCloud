# ----------------------------------------------------------------------
# FASE 1: BUILD
# ----------------------------------------------------------------------
FROM gradle:8.8-jdk21 AS build
WORKDIR /app

# Copiar archivos de configuración de Gradle
COPY backend/build.gradle backend/settings.gradle ./
COPY backend/gradle ./gradle
COPY backend/gradlew backend/gradlew.bat ./

# Copiar código fuente
COPY backend/src ./src

# Descargar dependencias
RUN ./gradlew dependencies --no-daemon

# Construir el JAR
RUN ./gradlew bootJar --no-daemon

# Renombrar el JAR (asegurándose de que existe)
RUN ls -la build/libs/ && \
    cp build/libs/*.jar build/libs/app.jar && \
    ls -la build/libs/app.jar

# ----------------------------------------------------------------------
# FASE 2: RUNTIME
# ----------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
EXPOSE 8080

# Copiar el JAR desde la fase de build
COPY --from=build /app/build/libs/app.jar ./app.jar

# Verificar que el archivo existe
RUN ls -la app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]