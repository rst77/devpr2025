package com.r13a.rinha2025.client;

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
import java.util.logging.Logger;

import com.r13a.rinha2025.Service;
import com.r13a.rinha2025.entity.Health;
import com.r13a.rinha2025.entity.Payment;

public class PaymentsClient {

    private static boolean ativoA = true;
    private static boolean ativoB = true;
    private static int reqTimeoutA = 100;
    private static int reqTimeoutB = 100;

    private static final byte[] REQUESTED_AT_PREFIX = "{\"requestedAt\":\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REQUESTED_AT_SUFFIX = "\",".getBytes(StandardCharsets.UTF_8);

    private static final ThreadLocal<ByteBuffer> BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(256));
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC);

    public enum Acao {
        NOVO, CHAMA_A, CHAMA_B, REPROCESSA
    };

    private final HttpClient clientA = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Service.CONN_TO - 10))
            .build();

    private final HttpClient clientB = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Service.CONN_TO))
            .build();

    private byte[] original; // Guarda o registro original recebido.

    /**
     * Atualiza os status dos serviços.
     */
    public static void setStatus(Health cd) {
        ativoA = cd.isStatusA();
        reqTimeoutA = cd.getMinResponseTimeA();
        ativoB = cd.isStatusB();
        reqTimeoutB = cd.getMinResponseTimeB();

        if (reqTimeoutA == 0) reqTimeoutA = 1;
        if (reqTimeoutB == 0) reqTimeoutB = 1;

    }

    public void processPayment(byte[] pd, Acao servico) {
        if (servico == Acao.NOVO)
            original = pd;
        else
            pd = original;

        if (servico == Acao.REPROCESSA) {
            Service.processamento.offer(original);
            return;
        }

        ByteBuffer buffer = BUFFER.get().clear();
        buffer.put(REQUESTED_AT_PREFIX);

        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        buffer.put(FORMATTER.format(now).getBytes(StandardCharsets.UTF_8));

        buffer.put(REQUESTED_AT_SUFFIX);

        buffer.put(pd, 1, pd.length - 1);

        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);

        try {
            if (isAReady() && (reqTimeoutA / reqTimeoutB) < 0.5) {
            // if (((servico == Acao.NOVO || servico == Acao.CHAMA_A) && isAReady()) ||
            //         (isBReady() && (reqTimeoutA / reqTimeoutB) < 2)) {
                chamaA(result);
            } else if (isBReady()) {
                chamaB(result);
            }
            else {
                processPayment(pd, Acao.REPROCESSA);
            }

        } catch (Exception e) {
            // Só vai
        }

    }

    private void addProcessamento(byte[] body, byte servico) {

        String dados = new String(body, StandardCharsets.UTF_8);

        Payment p = new Payment();
        int cont = 0;
        for (String keyValue : dados.split(",")) {
            try {
                // String[] data = keyValue.split(":");
                switch (cont) {
                    case 0 -> p.setRequestedAt(
                            Instant.parse(keyValue.subSequence(16, keyValue.indexOf("Z") + 1)).toEpochMilli());
                    case 2 -> p.setAmount(
                            Double.parseDouble(
                                    String.valueOf(
                                            keyValue.subSequence(keyValue.indexOf(":") + 1, keyValue.indexOf("}")))
                                            .trim()));
                }
                cont++;

            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }

        if (servico == 1)
            Service.resultadoA.put(p);
        else
            Service.resultadoB.put(p);

    }

    public void chamaA(byte[] pd) {

        try {

            HttpRequest requestA = HttpRequest.newBuilder()
                    .uri(URI.create(Service.urlA + "/payments"))
                    .timeout(java.time.Duration.ofSeconds(Service.REQ_TO - 5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(pd))
                    .build();

            HttpResponse<String> resp = clientA.send(requestA, BodyHandlers.ofString());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                addProcessamento(pd, (byte) 1);
            } else {
                processPayment(pd, Acao.CHAMA_B);
            }

            resp = null;
            requestA = null;
        } catch (IOException | InterruptedException ex) {
            processPayment(pd, Acao.CHAMA_B);
        }
    }

    public void chamaB(byte[] pd) {
        try {

            HttpRequest requestB = HttpRequest.newBuilder()
                    .uri(URI.create(Service.urlB + "/payments"))
                    .timeout(java.time.Duration.ofSeconds(Service.REQ_TO))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(pd)).expectContinue(true)
                    .build();

            HttpResponse<String> resp = clientB.send(requestB, BodyHandlers.ofString());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                addProcessamento(pd, (byte) 2);
            } else if (resp.statusCode() == 422) {
                // registro duplicado
            } else
                processPayment(pd, Acao.REPROCESSA);

            resp = null;
            requestB = null;

        } catch (HttpTimeoutException ex) {
            processPayment(pd, Acao.REPROCESSA);
        } catch (IOException | InterruptedException ex) {
            processPayment(pd, Acao.REPROCESSA);
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

        return true;

    }

}
