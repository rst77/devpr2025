#!/bin/bash

tag="ghcr.io/rst77/app:9"

echo TEST - $(date +"%Y-%m-%d at %H:%M:%S") - $tag > src/main/resources/version.fingerprint
docker compose down
mvn clean compile assembly:single
docker build -t $tag -f vm.Dockerfile  . --no-cache --progress=plain
docker push
