#!/bin/bash

tag="ghcr.io/rst77/app:9"

echo $(date +"%Y-%m-%d at %H:%M:%S") - $tag > src/main/resources/version.fingerprint


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
    docker image rm $tag
    docker build -t $tag -f app.Dockerfile  . --no-cache --progress=plain
    docker push     $tag
    docker login --username rst77 --password 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 ghcr.io
    docker push ghcr.io/rst77/app:9
fi