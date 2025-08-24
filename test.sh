#!/bin/bash
set -x

# imagem do test
tag="ghcr.io/rst77/app:20"
# nos ativos
nos=2

echo Iniciando....

testa() {

    sudo chown ubuntu:ubuntu ./pgo/node01/default.iprof
    docker compose --env-file env/v${MAX_WORKERS}.env -f docker-compose-pgo.yml up &
    sleep 5s

    k6 run rinha-test/rinha.js

    sleep 3s
    sudo chown ubuntu:ubuntu ./pgo/node01/*
    docker compose --env-file env/v${MAX_WORKERS}.env -f docker-compose-pgo.yml down &

    ./build.sh opt

    docker compose --env-file env/v${MAX_WORKERS}.env -f docker-compose.yml up -d 
    sleep 5s

    k6 run rinha-test/rinha.js
    mv  partial-results.json results/tst-${nos}-${MAX_REQUESTS}-${MAX_WORKERS}-partial-results.json
    docker compose --env-file env/v${MAX_WORKERS}.env -f docker-compose.yml down

}


docker compose -f docker-compose-pgo.yml down
#./build.sh pgo

export MAX_REQUESTS=500

### Teste 500vus 10s ###
export MAX_WORKERS=10
echo Chamando teste 1
testa

read -p "Continue? (Y/N): "


### Teste 500vus 20s ###
export MAX_WORKERS=20
echo Chamando teste 1
testa

### Teste 500vus 30s ###
export MAX_WORKERS=30
testa

### Teste 500vus 40s ###
export MAX_WORKERS=40
testa

### Teste 500vus 50s ###
export MAX_WORKERS=50
testa

### Teste 500vus 60s ###
export MAX_WORKERS=50
testa

### Teste 500vus 60s ###
export MAX_WORKERS=50
testa





