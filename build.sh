#!/bin/bash

if [ "$1" != "build" ]; then
    mvn clean compile
fi

if [ "$1" == "fast" ]; then
    mvn -Pnative-a-fast  package
    cp target/api-fast target/api-App
    mvn -Pnative-c-fast  package
    cp target/cli-fast target/cli-App
elif [ "$1" == "pgo" ]; then
    mvn -Pnative-a-pgo  package
    mvn -Pnative-c-pgo  package
elif [ "$1" == "opt" ]; then
    mvn -Pnative-a-opt  package
    mvn -Pnative-c-opt  package
fi

if [ "$1" != "pgo" ]; then
    docker compose down
    docker image rm ghcr.io/rst77/devpr2025/api-app:5
    docker image rm ghcr.io/rst77/devpr2025/cli-app:5
    docker build -t ghcr.io/rst77/devpr2025/api-app:5 -f api.Dockerfile  . --no-cache --progress=plain
    docker build -t ghcr.io/rst77/devpr2025/cli-app:5 -f cli.Dockerfile  . --no-cache --progress=plain
    docker push     ghcr.io/rst77/devpr2025/api-app:5
    docker push     ghcr.io/rst77/devpr2025/cli-app:5
    docker compose push
    docker compose up
fi