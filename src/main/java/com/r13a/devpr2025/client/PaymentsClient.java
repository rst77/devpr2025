package com.r13a.devpr2025.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.r13a.devpr2025.grpc.ControlData;
import com.r13a.devpr2025.grpc.PaymentData;
import com.r13a.devpr2025.grpcservices.PaymentsBackClient;

public class PaymentsClient {
    private static final Logger logger = Logger.getLogger(PaymentsClient.class.getName());

    private static String urlA = null;
    private static String urlB = null;

    // Valores de controle do comportamento.
    private static boolean ativoA = true;
    private static boolean ativoB = true;
    private static int connTimeoutA = 30;
    private static int reqTimeoutA = 100;
    private static int connTimeoutB = 30;
    private static int reqTimeoutB = 100;
    HttpRequest.BodyPublisher body;

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

    public static void setStatus(ControlData cd) {
        logger.info(">>>---> Atualizado status");
        ativoA = cd.getStatusA() == 1 ? true : false;
        reqTimeoutA = cd.getReqTimeoutA();
        ativoB = cd.getStatusB() == 1 ? true : false;
        reqTimeoutB = cd.getReqTimeoutB();
    }

    public void processPayment(PaymentData pd) {

        // Ajusta a hora da chamada
        pd = pd.toBuilder().setRequestedAt(Instant.now().toString()).build();

        body = BodyPublishers.ofString(
                "{" +
                        "\"correlationId\": \"" + pd.getCorrelationId() + "\"," +
                        "\"amount\": " + pd.getAmount() + "," +
                        "\"requestedAt\" : \"" + pd.getRequestedAt() + "\"" +
                        "}");

        try {
            if (isAReady() && (isBReady() && (reqTimeoutA/reqTimeoutB) < 2)) {
                chamaA(pd);
            } else if (!isAReady() && isBReady() ) {
                chamaB(pd);
            } else {
                PaymentsBackClient.rebote.add(pd);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Problemas no processamento de decisao do cliente.");
        }

    }

    public void chamaA(PaymentData pd) {

        try {
            HttpClient clientA = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(30))
                    .build();

            HttpRequest requestA = HttpRequest.newBuilder()
                    .uri(URI.create(PaymentsClient.urlA + "/payments"))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(body)
                    .build();

            HttpResponse<String> resp = clientA.send(requestA, BodyHandlers.ofString());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300)
                PaymentsBackClient.resultado.add(pd.toBuilder().setService(1).build());
            else
                processPayment(pd);

        } catch (IOException | InterruptedException ex) {
            logger.log(Level.INFO, ">>>---> Erro na chamada do payment DEFAULT - {0} / {1}", new Object[] { ex.getMessage(), ex.getClass().toString() });
                processPayment(pd);

        }
    }

    public void chamaB(PaymentData pd) {
        try {
            HttpClient clientB = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(30))
                    .build();

            HttpRequest requestB = HttpRequest.newBuilder()
                    .uri(URI.create(PaymentsClient.urlB + "/payments"))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(body)
                    .build();

            HttpResponse<String> resp = clientB.send(requestB, BodyHandlers.ofString());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                PaymentsBackClient.resultado.add(pd.toBuilder().setService(2).build());
            }
            else
                PaymentsBackClient.rebote.add(pd);

        } catch (IOException | InterruptedException ex) {
            logger.log(Level.INFO, ">>>---> Erro na chamada do payment FALLBACK - {0} / {1}",
                    new Object[] { ex.getMessage(), ex.getClass().toString() });
            PaymentsBackClient.rebote.add(pd);
        }

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
