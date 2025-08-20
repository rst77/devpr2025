FROM container-registry.oracle.com/graalvm/jdk:21

WORKDIR /app

COPY target/devpr2025-0.1-jar-with-dependencies.jar /app

EXPOSE 9999

ENTRYPOINT ["java", "-jar", "devpr2025-0.1-jar-with-dependencies.jar", "--gc=serial"]