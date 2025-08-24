FROM alpine:latest

WORKDIR /app

RUN apk add libstdc++
RUN apk add gcompat
RUN mkdir -p /app/pgo
COPY target/app /app/app
EXPOSE 9999

CMD ["/app/app", "-Xmx80m", "-XX:MaxRAM=80m","-XX:MaxDirectMemorySize=80m"]

#"-Xms20m","-Xmn1m",
#"/usr/bin/time", "-v", "-o", "time.out",

#,"-XX:+PrintGC","-XX:+VerboseGC"