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

    /**
     * Atualiza os status dos serviços.
     */
    public static void setStatus(Health cd) {
        ativoA = cd.isStatusA();
        reqTimeoutA = cd.getMinResponseTimeA();
        ativoB = cd.isStatusB();
        reqTimeoutB = cd.getMinResponseTimeB();

    }

    private byte[] original;
    public void processPayment(byte[] pd, byte servico) {
        if (servico == 0)
            original = pd;
        else 
            pd = original;

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
            if ((servico < 2 && isAReady()) && (isBReady() && (reqTimeoutA/reqTimeoutB) < 2)) {
                chamaA(result);
            } else if (isBReady() ) {
                chamaB(result);
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
                    case 0 -> p.setRequestedAt( Instant.parse(keyValue.subSequence(16, keyValue.indexOf("Z") + 1)).toEpochMilli() );
                    case 2 -> p.setAmount(
                                            Double.parseDouble(
                                                String.valueOf(
                                                    keyValue.subSequence(keyValue.indexOf(":") +1, keyValue.indexOf("}"))
                                                ).trim()
                                            )
                                        );
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


            HttpClient clientA = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Service.CONN_TO - 10))
                    .build();
                    
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
                addProcessamento(pd, (byte)1);
            }
            else {
                processPayment(pd, (byte)2);
            }
                

        } catch (HttpTimeoutException ex) {
            processPayment(pd, (byte)2);
        } catch (IOException | InterruptedException ex) {
                processPayment(pd, (byte)2);

        }
    }

            HttpClient clientB = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Service.CONN_TO))
                    .build();
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
                addProcessamento(pd, (byte)2);
            }
            else if (resp.statusCode() == 422) {
                    // registro duplicado
            }
            else
               processPayment(pd, (byte)2);

        } catch (HttpTimeoutException ex) {
            // Faz parte.
        } catch (IOException | InterruptedException ex) {
            processPayment(pd, (byte)2);
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
