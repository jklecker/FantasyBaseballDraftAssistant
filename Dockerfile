# ── Stage 1: build React ──────────────────────────────────────────────────────
FROM node:20-alpine AS frontend
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm install --legacy-peer-deps --no-audit --no-fund
COPY frontend/src ./src
COPY frontend/public ./public
ENV SKIP_PREFLIGHT_CHECK=true
ENV GENERATE_SOURCEMAP=false
RUN npm run build

# ── Stage 2: build Spring Boot JAR ───────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS backend
WORKDIR /app
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
# Embed the built React app — Spring Boot serves static files from here
COPY --from=frontend /frontend/build ./src/main/resources/static
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon -x test

# ── Stage 3: minimal runtime image ───────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
