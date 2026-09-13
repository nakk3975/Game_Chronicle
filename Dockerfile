FROM node:22-bookworm-slim AS frontend
WORKDIR /ui
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM gradle:8.14.3-jdk17 AS backend
WORKDIR /app
COPY settings.gradle ./
COPY backend/ backend/
COPY --from=frontend /ui/dist/ backend/src/main/resources/static/
RUN gradle --no-daemon :backend:test :backend:bootJar

FROM eclipse-temurin:17-jre-jammy
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=backend /app/backend/build/libs/backend-0.1.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
