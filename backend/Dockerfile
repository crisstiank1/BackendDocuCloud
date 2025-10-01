# ----------------------------------------------------------------------
# FASE 1: BUILD - Compila la aplicación usando Gradle
# ----------------------------------------------------------------------
# Usamos una imagen que tiene Java (21) y Gradle (8.x)
FROM gradle:8.8-jdk21 AS build

# Establece el directorio de trabajo dentro del contenedor
WORKDIR /app

# 1. Copia solo los archivos de configuración de Gradle. Esto permite que Docker cachee las dependencias.
# Si el build.gradle o settings.gradle no cambian, Docker no volverá a descargar las dependencias.
COPY build.gradle settings.gradle /app/
COPY gradle /app/gradle
COPY gradlew /app/

# Descarga las dependencias. Usamos --no-daemon para evitar procesos persistentes en Docker.
RUN ./gradlew dependencies --no-daemon

# 2. Copia el código fuente restante
COPY src /app/src

# Empaqueta la aplicación en un JAR ejecutable (bootJar es la tarea de Spring Boot)
RUN ./gradlew bootJar --no-daemon

# ----------------------------------------------------------------------
# FASE 2: RUNTIME - Crea la imagen final minimalista
# ----------------------------------------------------------------------
# Usamos JRE Alpine (más pequeño y seguro que la versión completa de JDK)
FROM eclipse-temurin:21-jre-alpine

# Define el puerto de la aplicación Spring Boot
EXPOSE 8080

# Define el directorio de trabajo y copia el archivo JAR de la fase anterior
WORKDIR /app
# Buscamos el JAR generado en la carpeta build/libs de la fase de 'build'
COPY --from=build /app/build/libs/*.jar app.jar

# Comando para ejecutar la aplicación JAR al iniciar el contenedor
ENTRYPOINT ["java", "-jar", "app.jar"]
