#!/bin/bash
set -x

workers=1

nos=4

diretorio=teste-${nos}-nos

MAX_REQUESTS=603


mkdir $diretorio

while [ $workers -le 100 ]; do

    echo PAYMENT_PROCESSORS: \"${workers}\" > env.envfile
    docker compose -f ../../rinha-de-backend-2025/payment-processor/docker-compose.yml down
    docker compose -f ../docker-compose.yml down
    sleep 5
    docker compose -f ../../rinha-de-backend-2025/payment-processor/docker-compose.yml up --build -d 1>/dev/null 2>&1
    docker compose --env-file env.envfile -f ../docker-compose.yml up --build > $diretorio/docker-compose-${workers}.logs &
    sleep 5
    k6 run -e MAX_REQUESTS=$MAX_REQUESTS --log-output=file=$diretorio/k6-final-${workers}.logs ../rinha-test/rinha-final.js

    mv final-results.json $diretorio/t-${workers}-final-results.json

    if [ "$workers" == 1 ]; then
        workers=5
    else
        workers=$((workers+5))
    fi

done