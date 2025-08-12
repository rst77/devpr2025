FROM alpine:latest

WORKDIR /app

RUN apk add libstdc++
RUN apk add gcompat

COPY target/api-App /app/api-App

EXPOSE 9999

CMD ["/app/api-App"]