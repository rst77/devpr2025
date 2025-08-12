package com.r13a.devpr2025.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.logging.Logger;

import com.r13a.devpr2025.grpc.ControlData;
import com.r13a.devpr2025.grpc.PaymentData;
import com.r13a.devpr2025.grpcservices.PaymentsBackClient;

public class PaymentsClient {
    private static final Logger logger = Logger.getLogger(PaymentsClient.class.getName());

    private static String urlA = null;
    private static String urlB = null;

    public PaymentsClient() {

        String defaultURL = System.getenv("PAYMENT_PROCESSOR_DEFAULT");
        if (defaultURL != null) {
            PaymentsClient.urlA = defaultURL;
        } else {
            PaymentsClient.urlA = "http://localhost:8001";
        }
        String fallbackURL = System.getenv("PAYMENT_PROCESSOR_FALLBACK");
        if (fallbackURL != null) {
            PaymentsClient.urlB = fallbackURL;
        } else {
            PaymentsClient.urlB = "http://localhost:8002";
        }

    }

    // Valores de controle do comportamento.
    private static boolean ativoA = true;
    private static boolean ativoB = true;
    private static int connTimeoutA = 30;
    private static int reqTimeoutA = 100;
    private static int connTimeoutB = 30;
    private static int reqTimeoutB = 100;
    HttpRequest.BodyPublisher body;

    public static void setStatus(ControlData cd) {
        // logger.info(">>>---> Atualizado status");
        ativoA = cd.getStatusA() == 1 ? true : false;
        reqTimeoutA = cd.getReqTimeoutA();
        ativoB = cd.getStatusB() == 1 ? true : false;
        reqTimeoutB = cd.getReqTimeoutB();
    }

    public void processPayment(PaymentData pd) {

        body = BodyPublishers.ofString(
                "{" +
                        "\"correlationId\": \"" + pd.getCorrelationId() + "\"," +
                        "\"amount\": " + pd.getAmount() + "," +
                        "\"requestedAt\" : \"" + pd.getRequestedAt() + "\"" +
                        "}");

        try {
            if (isAReady()) {
                chamaA(pd);
            } else if (!isAReady() && isBReady()) {
                chamaB(pd);
            } else {
                PaymentsBackClient.rebote.add(pd);
            }

        } catch (Exception e) {
            // System.out.println(">>>---> " + e.getMessage());
        }

    }

    public void chamaA(PaymentData pd) {

        HttpClient clientA = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(connTimeoutA))
                .build();

        HttpRequest requestA = HttpRequest.newBuilder()
                .uri(URI.create(PaymentsClient.urlA + "/payments"))
                .timeout(java.time.Duration.ofMillis(10000 + 100))
                .header("Content-Type", "application/json")
                .POST(body)
                .build();

        clientA.sendAsync(requestA, BodyHandlers.ofString())
                .thenAcceptAsync(responseA -> {

                    if (responseA.statusCode() == 200)
                        PaymentsBackClient.resultado.add(pd.toBuilder().setService(1).build());

                })
                .whenComplete((reponseA, exA) -> {
                    if (exA != null) {
                        System.err.println("\n E1 " + exA.getMessage());
                        if (!isBReady())
                            PaymentsBackClient.rebote.add(pd);
                        else
                            chamaB(pd);
                    }
                })
                .join();
        // System.out.print("1 ");

    }

    public void chamaB(PaymentData pd) {
        HttpClient clientB = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(connTimeoutB))
                .build();

        HttpRequest requestB = HttpRequest.newBuilder()
                .uri(URI.create(PaymentsClient.urlB + "/payments"))
                .timeout(java.time.Duration.ofMillis(10000 + 100))
                .header("Content-Type", "application/json")
                .POST(body)
                .build();

        clientB.sendAsync(requestB, BodyHandlers.ofString())
                .thenAcceptAsync(responseB -> {

                    if (responseB.statusCode() == 200)
                        PaymentsBackClient.resultado.add(pd.toBuilder().setService(2).build());
                    else
                        PaymentsBackClient.rebote.add(pd);

                })
                .whenComplete((reponseA, exA) -> {
                    if (exA != null) {
                        System.err.print("E2 " + exA.getMessage());
                        PaymentsBackClient.rebote.add(pd);
                    }
                })
                .join();
        // System.out.print("2 ");

    }

    public static boolean isAlmostReady() {

        if (isAReady() || isBReady())
            return true;

        return false;

    }

    public static boolean isFullReady() {

        if (isAReady() && isBReady())
            return true;

        return false;

    }

    public static boolean isAReady() {

        if (PaymentsClient.ativoA)
            return true;

        return false;

    }

    public static boolean isBReady() {

        if (PaymentsClient.ativoB)
            return true;

        return false;

    }

}
