# syntax=docker/dockerfile:1
# =====================================================================
#  Imagen de wisp-gestion para Render (plan de 512 MB de RAM).
#  Etapa 1 compila con el JDK; etapa 2 solo lleva el JRE y el .jar.
# =====================================================================

# ---------- Etapa 1: compilar ----------
FROM eclipse-temurin:21-jdk-jammy AS compilacion
WORKDIR /fuente

# Primero solo lo necesario para descargar dependencias: Docker reutiliza esta capa
# mientras no cambie pom.xml, y las siguientes compilaciones son mucho más rápidas.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Las pruebas se ejecutan antes de publicar (./mvnw test); aquí solo se empaqueta
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar app.jar

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:21-jre-jammy

# La app no corre como root
RUN groupadd --system wisp && useradd --system --gid wisp --no-create-home wisp
WORKDIR /app
COPY --from=compilacion --chown=wisp:wisp /fuente/app.jar app.jar
USER wisp

# prod: SSL obligatorio con la base de datos y lectura de la IP real detrás del proxy de Render
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=America/Lima

# Memoria para 512 MB en total (lo que no es heap también ocupa RAM):
#   heap 50% (~256 MB) + metaspace <= 140 MB + code cache 48 MB + direct 32 MB + hilos (pila de 512 KB)
#   SerialGC: el recolector que menos memoria usa (ideal con 1 CPU)
#   TieredStopAtLevel=1: compilación JIT más liviana (menos memoria, arranque más rápido)
#   ExitOnOutOfMemoryError: si se queda sin memoria, se reinicia en vez de quedar colgada
#   user.timezone: fechas de "hoy" en hora de Perú aunque el servidor esté en UTC
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:InitialRAMPercentage=25 -XX:+UseSerialGC \
-XX:MaxMetaspaceSize=140m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -Xss512k \
-XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Duser.timezone=America/Lima"

# Render indica el puerto real en la variable PORT (application.properties la usa)
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
