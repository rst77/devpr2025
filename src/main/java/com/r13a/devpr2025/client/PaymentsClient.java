package com.r13a.devpr2025.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.r13a.devpr2025.Service;
import com.r13a.devpr2025.entity.Health;
import com.r13a.devpr2025.entity.Payment;

public class PaymentsClient {
    private static final Logger logger = Logger.getLogger(PaymentsClient.class.getName());

    // Valores de controle do comportamento.
    private static boolean ativoA = true;
    private static boolean ativoB = true;

    private static int reqTimeoutA = 100;

    private static int reqTimeoutB = 100;
    HttpRequest.BodyPublisher body;

    private static final byte[] REQUESTED_AT_PREFIX = "{\"requestedAt\":\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REQUESTED_AT_SUFFIX = "\",".getBytes(StandardCharsets.UTF_8);

    private static final ThreadLocal<ByteBuffer> BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(256));
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC);

    private static final PaymentsClient instance;

    static {
        instance = new PaymentsClient();
    }

    public static PaymentsClient getInstance() {
        return instance;
    }

    public static void setStatus(Health cd) {
        // logger.info(">>>---> Atualizado status");
        ativoA = cd.isStatusA();
        reqTimeoutA = cd.getMinResponseTimeA();
        ativoB = cd.isStatusB();
        reqTimeoutB = cd.getMinResponseTimeB();
        logger.log(Level.INFO, ">>>>>---------------------------------------> Threads Ativas: [{0}]",
                Thread.activeCount());

    }

    public void processPayment(byte[] pd) {

        try {
            ByteBuffer buffer = BUFFER.get().clear();
            buffer.put(REQUESTED_AT_PREFIX);

            Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
            buffer.put(FORMATTER.format(now).getBytes(StandardCharsets.UTF_8));

            buffer.put(REQUESTED_AT_SUFFIX);

            buffer.put(pd, 1, pd.length - 1);

            byte[] result = new byte[buffer.position()];
            buffer.flip();
            buffer.get(result);

            if (isAReady() && (isBReady() && (reqTimeoutA / reqTimeoutB) < 2)) {
                chamaA(result);
            } else if (isBReady()) {
                chamaB(result);
                // } else {
                // Service.rebote.add(pd);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Problemas no processamento de decisao do cliente.");
            e.printStackTrace();

        }

    }

    public void chamaA(byte[] pd) {

        try {
            HttpClient clientA = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(30))
                    .build();

            HttpRequest requestA = HttpRequest.newBuilder()
                    .uri(URI.create(Service.urlA + "/payments"))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(pd))
                    .build();

            HttpResponse<String> resp = clientA.send(requestA, BodyHandlers.ofString());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300)
                addProcessamento(pd, 1);
            else {
                if (isBReady())
                    chamaB(pd);
            }

        } catch (HttpTimeoutException ex) {
            processPayment(pd);
        } catch (IOException | InterruptedException ex) {
            logger.log(Level.INFO, ">>>---> Erro na chamada do payment DEFAULT - {0} / {1}",
                    new Object[] { ex.getMessage(), ex.getClass().toString() });
            processPayment(pd);

        }
    }

    public void chamaB(byte[] pd) {
        try {
            HttpClient clientB = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(30))
                    .build();

            HttpRequest requestB = HttpRequest.newBuilder()
                    .uri(URI.create(Service.urlB + "/payments"))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(pd))
                    .build();

            HttpResponse<String> resp = clientB.send(requestB, BodyHandlers.ofString());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300)
                addProcessamento(pd, 2);

        } catch (HttpTimeoutException ex) {
            // Faz parte.
        } catch (IOException | InterruptedException ex) {
            logger.log(Level.INFO, ">>>---> Erro na chamada do payment FALLBACK - {0} / {1}",
                    new Object[] { ex.getMessage(), ex.getClass().toString() });
            // Service.rebote.add(pd);
        }

    }

    private void addProcessamento(byte[] body, int servico) {

        String dados = new String(body, StandardCharsets.UTF_8);

        Payment p = new Payment();

        int cont = 0;
        for (String keyValue : dados.split(",")) {
            try {
                // String[] data = keyValue.split(":");
                switch (cont) {
                    case 0 -> p.setRequestedAt(Instant.parse(keyValue.subSequence(16, keyValue.indexOf("Z") + 1)));
                    case 3 ->
                        p.setAmount(Double.parseDouble(String.valueOf(keyValue.subSequence(9, keyValue.indexOf("}")))));
                }

                cont++;
            } catch (Exception e) {
                System.out.println(keyValue);
                e.printStackTrace();
                System.exit(-1);
            }
        }

        p.setService(servico);

        Service.resultado.put(p.getRequestedAt().toEpochMilli(), p);

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

        // if (PaymentsClient.ativoB)
        return true;

        // return false;

    }

}
