#!/bin/bash

if [ "$1" != "build" ]; then
    mvn clean compile
fi

if [ "$1" == "fast" ]; then
    mvn -Pnative-fast  package
    cp target/app-fast target/app
elif [ "$1" == "pgo" ]; then
    mvn -Pnative-pgo  package
elif [ "$1" == "opt" ]; then
    mvn -Pnative-opt  package
fi

if [ "$1" != "pgo" ]; then
    docker compose down &
    docker image rm ghcr.io/rst77/app:7
    docker build -t ghcr.io/rst77/app:7 -f app.Dockerfile  . --no-cache --progress=plain
    docker push     ghcr.io/rst77/app:7
    docker compose push
fi