#!/bin/bash

tag="ghcr.io/rst77/app:20"

echo $(date +"%Y-%m-%d at %H:%M:%S") - $tag > src/main/resources/version.fingerprint


if [ "$1" != "build" ]; then
    mvn clean compile
fi

if [ "$1" == "fast" ]; then
    mvn -Pnative-fast  package
    cp target/app-fast target/app
elif [ "$1" == "pgo" ]; then
    mvn -Pnative-pgo  package
    cp target/app-ins pgo/node01/app
    cp target/app-ins pgo/node02/app
elif [ "$1" == "opt" ]; then
    mvn -Pnative-opt  package
    cp target/app pgo/node01/app
    cp target/app pgo/node02/app
fi

if [ "$1" != "pgo" ]; then
    docker-compose down &
    docker image rm $tag
    docker build -t $tag -f app.Dockerfile  . --no-cache 
    docker push     $tag
    docker push $tag
fi

if [ "$1" == "pgo" ]; then
 cp target/app-ins target/app
    docker-compose -f docker-compose-pgo.yml down 
    docker image rm $tag
    docker build -t $tag -f app.Dockerfile  . --no-cache 
    docker push     $tag
    docker push $tag
    docker-compose -f docker-compose-pgo.yml up 
fi
