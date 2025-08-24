#!/bin/bash
set -x

mvn clean compile assembly:single
docker build -t ghcr.io/rst77/app:99 -f vm.Dockerfile . --no-cache
docker push ghcr.io/rst77/app:99
docker compose -f docker-compose.yml up