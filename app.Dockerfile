FROM alpine:latest

WORKDIR /app

RUN apk add libstdc++
RUN apk add gcompat
RUN mkdir -p /app/pgo
COPY target/app /app

EXPOSE 9999

CMD ["/app/app"]