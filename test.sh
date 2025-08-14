

docker compose -f docker-compose-vm.yml down
mvn clean compile assembly:single
docker build -t ghcr.io/rst77/app:6 -f vm.Dockerfile  . --no-cache --progress=plain