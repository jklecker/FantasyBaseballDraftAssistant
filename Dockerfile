# ── Stage 1: build React ──────────────────────────────────────────────────────
FROM node:20-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/.npmrc frontend/vite.config.js frontend/index.html ./
RUN npm install --no-audit --no-fund
COPY frontend/src ./src
RUN npm run build

# ── Stage 2: build Spring Boot JAR ───────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS backend
WORKDIR /app
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
# Belt: copy directly into src/main/resources/static so Spring Boot picks it up
# as classpath:/static/ without needing Gradle config.
COPY --from=frontend /frontend/build ./src/main/resources/static
# Suspenders: also place at frontend/build so Gradle processResources config works too.
COPY --from=frontend /frontend/build ./frontend/build
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon -x test

# ── Stage 3: minimal runtime image ───────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
