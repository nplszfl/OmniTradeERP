# 租户服务Dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/erp-tenant-service-*.jar app.jar
EXPOSE 8095
ENTRYPOINT ["java", "-jar", "app.jar"]