package com.r13a.devpr2025.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r13a.devpr2025.grpc.PaymentData;
import com.r13a.devpr2025.grpc.PaymentData.Builder;
import com.r13a.devpr2025.grpcservices.PaymentsBackClient;

public class PaymentsClient {
    private static final Logger logger = Logger.getLogger(PaymentsClient.class.getName());

    private final String urlA = "http://localhost:8001";
    private final String urlB = "http://localhost:8002";

    // Valores de controle do comportamento.
    private static boolean ativoA = true;
    private static boolean ativoB = true;
    private static int connTimeoutA = 30;
    private static int reqTimeoutA = 100;
    private static int connTimeoutB = 30;
    private static int reqTimeoutB = 100;
    HttpRequest.BodyPublisher body;

    public void processPayment(PaymentData pd) {

        body = BodyPublishers.ofString(
                "{" +
                        "\"correlationId\": \"" + pd.getCorrelationId() + "\"," +
                        "\"amount\": " + pd.getAmount() + "," +
                        "\"requestedAt\" : \"" + pd.getRequestedAt() + "\"" +
                        "}");

        try {
            if (ativoA && reqTimeoutA < 1000) {
                chamaA(pd);
            } else if (!ativoA && ativoB && reqTimeoutB < 1000) {
                chamaB(pd);
            } else {
                PaymentsBackClient.rebote.add(pd);
            }

        } catch (Exception e) {
            //System.out.println(">>>---> " + e.getMessage());
        }

    }

    public void chamaA(PaymentData pd) {

        HttpClient clientA = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(connTimeoutA))
                .build();

        HttpRequest requestA = HttpRequest.newBuilder()
                .uri(URI.create(urlA + "/payments"))
                .timeout(java.time.Duration.ofMillis(reqTimeoutA))
                .header("Content-Type", "application/json")
                .POST(body)
                .build();

        clientA.sendAsync(requestA, BodyHandlers.ofString())
                .thenAcceptAsync(responseA -> {

                    System.out.print("1 ");
                    if (responseA.statusCode() == 200)
                        PaymentsBackClient.resultado.add(pd.toBuilder().setService(1).build());

                })
                .whenComplete((reponseA, exA) -> {
                    if (exA != null) {
                        System.err.print("E1 ");
                        PaymentsBackClient.rebote.add(pd);
                    }
                })
                .join();
    }

    public void chamaB(PaymentData pd) {
        HttpClient clientB = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(connTimeoutB))
                .build();

        HttpRequest requestB = HttpRequest.newBuilder()
                .uri(URI.create(urlB + "/payments"))
                .timeout(java.time.Duration.ofMillis(reqTimeoutB))
                .header("Content-Type", "application/json")
                .POST(body)
                .build();

        clientB.sendAsync(requestB, BodyHandlers.ofString())
                .thenAcceptAsync(responseB -> {

                    System.out.print("2 ");
                    if (responseB.statusCode() == 200)
                        PaymentsBackClient.resultado.add(pd.toBuilder().setService(2).build());

                })
                .whenComplete((reponseA, exA) -> {
                    if (exA != null) {
                        System.err.print("E2 ");
                        PaymentsBackClient.rebote.add(pd);
                    }
                })
                .join();

    }

    public void processHealth() {

        HttpClient clientA = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(2000))
                .build();

        HttpRequest requestA = HttpRequest.newBuilder()
                .uri(URI.create(urlA + "/payments/service-health"))
                .timeout(java.time.Duration.ofMillis(3000))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpClient clientB = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(2000))
                .build();

        HttpRequest requestB = HttpRequest.newBuilder()
                .uri(URI.create(urlB + "/payments/service-health"))
                .timeout(java.time.Duration.ofMillis(3000))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        try {

            HttpResponse<String> respA = clientA.send(requestA, BodyHandlers.ofString());
            record Status(boolean failing, int minResponseTime) {
            }
            ObjectMapper mapa = new ObjectMapper();
            Status registroA = null;
            Status registroB = null;

            if (respA.statusCode() == 200) {
                registroA = mapa.readValue(respA.body(), Status.class);

                System.out.println("\nAntes - ativoA: " + ativoA);
                System.out.println("Antes - reqTimeoutA: " + reqTimeoutA);

                ativoA = !registroA.failing;
                reqTimeoutA = registroA.minResponseTime < 100 ? 100 : registroA.minResponseTime;

                System.out.println("Depois - ativoA: " + ativoA);
                System.out.println("Depois - reqTimeoutA: " + reqTimeoutA);

            }

            HttpResponse<String> respB = clientB.send(requestB, BodyHandlers.ofString());
            if (respB.statusCode() == 200) {
                registroB = mapa.readValue(respB.body(), Status.class);

                System.out.println("\nAntes - ativoB: " + ativoB);
                System.out.println("Antes - reqTimeoutB: " + reqTimeoutB);

                ativoB = !registroB.failing;
                reqTimeoutB = registroB.minResponseTime < 100 ? 100 : registroB.minResponseTime;

                System.out.println("Depois - ativoB: " + ativoB);
                System.out.println("Depois - reqTimeoutB: " + reqTimeoutB);
            }

        } catch (Exception e) {
            logger.info(">>>---> " + e.getMessage());
        }

    }

}
