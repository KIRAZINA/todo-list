# Runtime image (jar pre-built in target/)
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S appgroup && adduser -S -G appgroup -u 10001 appuser
WORKDIR /app
COPY target/*.jar app.jar
RUN chown appuser:appgroup /app /app/app.jar
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
