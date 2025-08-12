FROM alpine:latest

WORKDIR /app

RUN apk add libstdc++
RUN apk add gcompat

COPY ./target/cli-App cli-App

CMD ["/app/cli-App"]