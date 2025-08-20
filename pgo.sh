#!/bin/bash
set -x

tag="ghcr.io/rst77/app:99"


 cp target/app-ins target/app
    docker-compose -f docker-compose-pgo.yml down 
    docker image rm $tag
    docker build -t $tag -f app.Dockerfile  . --no-cache 
    docker push     $tag
    docker push $tag
    docker-compose -f docker-compose-pgo.yml up 
    sudo chown ubuntu:ubuntu /home/ubuntu/dev/rinha-2025/pgo/node01/default.iprof
    sudo chown ubuntu:ubuntu /home/ubuntu/dev/rinha-2025/pgo/node02/default.iprof

