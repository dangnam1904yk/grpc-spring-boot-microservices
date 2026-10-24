# Build 1 service bất kỳ trong multi-module:
#   docker build --build-arg SERVICE=apigateway -t apigateway .
#   docker build --build-arg SERVICE=category   -t category .
# Chạy cả hệ thống: docker compose up -d --build
FROM maven:3.8.7-eclipse-temurin-17 AS build
ARG SERVICE=apigateway

WORKDIR /build
COPY . .
# -am: build kèm module phụ thuộc (common-protos), dùng chung version từ pom gốc.
# sharing=locked: các service build song song không ghi đè cache .m2 của nhau
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl ${SERVICE} -am clean package -DskipTests

FROM eclipse-temurin:17-jre
ARG SERVICE=apigateway
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /build/${SERVICE}/target/*.jar ./app.jar
USER app

# JVM tự giới hạn heap theo memory của container; thêm option qua biến JAVA_TOOL_OPTIONS
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java","-jar","app.jar"]
