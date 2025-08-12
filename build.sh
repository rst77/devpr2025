#mvn clean compile
#mvn -Pnative-a-opt  package
#mvn -Pnative-c-opt  package
docker image rm ghcr.io/rst77/devpr2025/api-app:2
docker image rm ghcr.io/rst77/devpr2025/cli-app:2
docker build -t ghcr.io/rst77/devpr2025/api-app:2 -f api.Dockerfile  .
docker build -t ghcr.io/rst77/devpr2025/cli-app:2 -f cli.Dockerfile  .
docker push     ghcr.io/rst77/devpr2025/api-app:2
docker push     ghcr.io/rst77/devpr2025/cli-app:2
