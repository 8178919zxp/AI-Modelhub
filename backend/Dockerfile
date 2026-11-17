# =============================================================================
# AI ModelHub — Spring Boot Backend
# Stage 1: Maven build (JDK 17)
# Stage 2: JRE runtime
# Port: 8080
# =============================================================================

# ── Stage 1: Build ──────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /build

# Cache Maven dependencies (layer reuse)
COPY pom.xml .
RUN mvn dependency:go-offline -B -q || true

# Compile and package
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ── Stage 2: Runtime ────────────────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app

# Non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

COPY --from=build /build/target/*.jar app.jar

# RAG 本地中文向量模型（可选）。
# models/ 目录里只有 README.md 会随仓库分发；模型文件（*.onnx / vocab.txt）被打包时
# 若已下载则一并进镜像，未下载则容器内 RAG 自动降级为纯全文检索。
# 注意：必须先确保 models 目录存在（仓库里有 README.md），否则 COPY 会失败。
COPY models ./models

# Create writable directories for file uploads / temp
RUN mkdir -p /app/uploads /app/temp && chown -R appuser:appuser /app

# curl for the healthcheck (eclipse-temurin JRE image ships without it)
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

USER appuser

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker

# /api/health is permitAll and returns 200 with no sensitive data.
# /actuator/** is denyAll, so it must NOT be used here.
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=60s \
    CMD curl -fsS http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
