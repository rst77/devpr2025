FROM alpine:latest

WORKDIR /app

RUN apk add libstdc++
RUN apk add gcompat
RUN mkdir -p /app/pgo
COPY target/app /app

EXPOSE 9999

CMD ["/usr/bin/time", "-v", "-o", "time.out", "/app/app"]

# , "-XX:MaxRAM=160000000"