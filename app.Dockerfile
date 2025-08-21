FROM alpine:latest

WORKDIR /app

RUN apk add libstdc++
RUN apk add gcompat
RUN mkdir -p /app/pgo
COPY target/app /app/app
EXPOSE 9999

CMD ["/usr/bin/time", "-v", "-o", "time.out", "/app/app","-Xmx140m", "-Xms50m","-Xmn20m","-XX:MaxRAM=140000000","-XX:MaxDirectMemorySize=140M"]

#,"-XX:+PrintGC","-XX:+VerboseGC"