package com.r13a.devpr2025.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.r13a.devpr2025.grpc.ControlData;
import com.r13a.devpr2025.grpcservices.PaymentsFrontServer;

public class HealthClient {

    private static final Logger logger = Logger.getLogger(PaymentsClient.class.getName());

    private static String urlA = null;
    private static String urlB = null;

    // Valores de controle do comportamento.
    private boolean ativoA;
    private boolean ativoB;
    private int reqTimeoutA = 100;
    private int reqTimeoutB = 100;
    HttpRequest.BodyPublisher body;

    private static HealthClient hc;
    private static Instant lastCheck = Instant.now();

    public static HealthClient getHealthCheck() {
        if (hc == null) {

            String defaultURL = System.getenv("PAYMENT_PROCESSOR_DEFAULT");
            if (defaultURL != null) {
                HealthClient.urlA = defaultURL;
            } else {
                HealthClient.urlA = "http://localhost:8001";
            }
            String fallbackURL = System.getenv("PAYMENT_PROCESSOR_FALLBACK");
            if (fallbackURL != null) {
                HealthClient.urlB = fallbackURL;
            } else {
                HealthClient.urlB = "http://localhost:8002";
            }

            hc = new HealthClient();
            lastCheck = Instant.now();

            logger.info(">>>---> Health URL A: " + urlA);
            logger.info(">>>---> Health URL B: " + urlB);
        }

        return hc;

    }

    public void processHealth() {

            HttpClient clientA = HttpClient.newBuilder()
                    .build();

            HttpRequest requestA = HttpRequest.newBuilder()
                    .uri(URI.create(HealthClient.urlA + "/payments/service-health"))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpClient clientB = HttpClient.newBuilder()
                    .build();

            HttpRequest requestB = HttpRequest.newBuilder()
                    .uri(URI.create(HealthClient.urlB + "/payments/service-health"))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            try {

                HttpResponse<String> respA = clientA.send(requestA, BodyHandlers.ofString());
                record Status(boolean failing, int minResponseTime) {
                }
                ObjectMapper mapa = new ObjectMapper();
                mapa.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
                Status registroA = null;
                Status registroB = null;

                if (respA.statusCode() == 200) {
                    registroA = mapa.readValue(respA.body(), Status.class);

                    ativoA = !registroA.failing;
                    reqTimeoutA = registroA.minResponseTime < 100 ? 100 : registroA.minResponseTime;

                    System.out.println("\nativoA: " + ativoA);
                    System.out.println("reqTimeoutA: " + reqTimeoutA);

                }

                HttpResponse<String> respB = clientB.send(requestB, BodyHandlers.ofString());
                if (respB.statusCode() == 200) {
                    registroB = mapa.readValue(respB.body(), Status.class);

                    ativoB = !registroB.failing;
                    reqTimeoutB = registroB.minResponseTime < 100 ? 100 : registroB.minResponseTime;

                    System.out.println("ativoB: " + ativoB);
                    System.out.println("reqTimeoutB: " + reqTimeoutB);
                }

                ControlData cd = ControlData.newBuilder()
                        .setStatusA(ativoA ? 1 : 0)
                        .setReqTimeoutA(reqTimeoutA)
                        .setStatusB(ativoB ? 1 : 0)
                        .setReqTimeoutB(reqTimeoutB)
                        .build();

                PaymentsFrontServer.cdList.add(cd);

            } catch (Exception e) {
                logger.warning(">>>---> Erro no processamento do health: " + e.getMessage());
            }

    }

}
