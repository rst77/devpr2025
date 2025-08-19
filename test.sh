#!/bin/bash

tag="ghcr.io/rst77/app:20"

echo TEST - $(date +"%Y-%m-%d at %H:%M:%S") - $tag > src/main/resources/version.fingerprint
docker compose  -f docker-compose.yml down
mvn clean compile assembly:single
cp target/devpr2025-0.1-jar-with-dependencies.jar pgo/node01
cp target/devpr2025-0.1-jar-with-dependencies.jar pgo/node02
docker build -t $tag -f vm.Dockerfile  . --no-cache --progress=plain
docker push
docker compose -f docker-compose.yml up
