package com.r13a.devpr2025.api;

import com.sun.net.httpserver.HttpServer;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.r13a.devpr2025.grpc.PaymentData;
import com.r13a.devpr2025.grpc.PaymentServiceGrpc;
import com.r13a.devpr2025.grpc.PaymentServiceGrpc.PaymentServiceStub;
import com.r13a.devpr2025.grpcservices.PaymentsFrontServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ApiService {
    private static final Logger logger = Logger.getLogger(ApiService.class.getName());

    int gRPCPport = 5000; // Or any desired port
    int httpPort = 9999;

    private Server grpcServer;
    private PaymentServiceStub stub;

    public static void main(String[] args) throws InterruptedException, IOException {
        new ApiService();
    }

    public ApiService() throws InterruptedException, IOException {

        HttpServer serverHttp = HttpServer.create(new InetSocketAddress(httpPort), 0);
        serverHttp.createContext("/payments", new Router());
        serverHttp.setExecutor(null); // Creates a default executor
        serverHttp.start();
        logger.info("Servidor HTTP - Iniciado na porta: " + httpPort);

        start();
        startMonitoring();
        blockUntilShutdown();
    }

    public void startMonitoring() {

    }

    public void start() throws IOException {
        grpcServer = ServerBuilder.forPort(gRPCPport)
                .addService(new PaymentsFrontServer())
                .build()
                .start();
        System.out.println("Server gRPC - Iniciado na porta: " + gRPCPport);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            stop();
            System.err.println("*** server shut down");
        }));
    }

    public void stop() {
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.awaitTermination();
        }
    }

    static class Router implements HttpHandler {

        ObjectMapper mapa = new ObjectMapper();

        record Status(int totalRequests, double totalAmout) {
        }

        final class Report {
            private Status a;
            private Status b;

            public Report(Status a, Status b) {
                this.a = a;
                this.b = b;
            }

            public Status getFallback() {
                return b;
            }

            public void setFallback(Status b) {
                this.b = b;
            }

            public Status getDefault() {
                return a;
            }

            public void setDefault(Status a) {
                this.a = a;
            }
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            if ("GET".equals(exchange.getRequestMethod())) {
                Thread.ofVirtual().start(() -> {
                    try {
                        Map<String, String> params = getParamMap(exchange.getRequestURI().getQuery());

                        int totalDefault = 0;
                        double somaDefault = 0;
                        int totalBackup = 0;
                        double somaBackup = 0;

                        //logger.info("\n>>----->  from: " + params.get("from"));
                        //logger.info(">>----->  to  : " + params.get("to"));

                        try {
                            List<Entry<Long, PaymentData>> list = PaymentsFrontServer.result
                                    .entrySet()
                                    .stream()
                                    .filter(e -> e.getKey() >= Instant.parse(params.get("from")).toEpochMilli() &&
                                            e.getKey() <= Instant.parse(params.get("to")).toEpochMilli())
                                    .toList();

                            for (Entry<Long, PaymentData> d : list) {
                                if (d.getValue().getService() == 1) {
                                    totalDefault++;
                                    somaDefault += d.getValue().getAmount();
                                } else {
                                    totalBackup++;
                                    somaBackup += d.getValue().getAmount();
                                }
                            }
                        } catch (Exception e) {
                            logger.info(">>>---> problema processamento da lista - " + e.getMessage());
                        }

                        Status a = new Status(totalDefault, somaDefault);
                        Status b = new Status(totalBackup, somaBackup);

                        Report r = new Report(a, b);

                        mapa.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
                        String rMapa = mapa.writeValueAsString(r);
                        logger.info(">>>---> resposta: " + rMapa);

                        exchange.sendResponseHeaders(200, rMapa.getBytes().length);
                        OutputStream os = exchange.getResponseBody();
                        mapa.writeValue(os, r);
                        os.close();
                    } catch (Exception e) {
                        logger.info(">>>---> problema processamento relatorio - " + e.getMessage());
                    }
                });

            } else if ("POST".equals(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                byte[] bytes = is.readAllBytes();

                Thread.ofVirtual().start(() -> {

                    record Payment(String correlationId, double amount) {
                    }
                    ObjectMapper mapa = new ObjectMapper();
                    mapa.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
                    try {
                        Payment payment = mapa.readValue(new String(bytes, StandardCharsets.UTF_8), Payment.class);

                        PaymentData pd = PaymentData.newBuilder()
                                .setCorrelationId(payment.correlationId())
                                .setAmount(payment.amount())
                                .setRequestedAt(Instant.now().toString())
                                .build();

                        PaymentsFrontServer.list.add(pd);

                        exchange.sendResponseHeaders(200, 0);
                        exchange.close();
                    } catch (Exception e) {
                    }
                });
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    public static Map<String, String> getParamMap(String query) {
        if (query == null || query.isEmpty())
            return Collections.emptyMap();

        return Stream.of(query.split("&"))
                .filter(s -> !s.isEmpty())
                .map(kv -> kv.split("=", 2))
                .collect(Collectors.toMap(x -> x[0], x -> x[1]));

    }
}
