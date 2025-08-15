package com.r13a.devpr2025;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.r13a.devpr2025.client.HealthClient;
import com.r13a.devpr2025.client.NodeClient;
import com.r13a.devpr2025.client.PaymentsClient;
import com.r13a.devpr2025.entity.Health;
import com.r13a.devpr2025.entity.Payment;
import com.sun.net.httpserver.HttpServer;

import io.undertow.Undertow;

public class Service {

    private static final Logger logger = Logger.getLogger(Service.class.getName());

    private final static int HTTP_PORT = 9901;
    private final static int MAX_THREAD = 900;
    public final static int DELETE_CAP = 5;
    public final static int SUMM_DELAY = 100;
    public final static int PAYMENT_WORKERS = 8;

    public static String NODE_ID;

    public final static LinkedTransferQueue<byte[]> processamento = new LinkedTransferQueue<>();
    public final static List<ArrayBlockingQueue<byte[]>> dist = new ArrayList<>();
    public static final Map<Long, Payment> resultado = new HashMap<>();

    public static String urlA = null;
    public static String urlB = null;

    public static String pairURL;
    public static String nodeURL;

    private HttpServer serverHttp;

    public static void main(String[] args) throws Exception {

        loadEnvVar();

        new Service();
    }

    public Service() throws Exception {
        // Inicia o monitoramento da saúde dos servicos de pagamento.
        startMonitoring();

        // Inicia o monitoramento do pagamento.
        startPayment();

        // Inicia Servidor Http.
        start();
    }

    /**
     * Monitora o estado de saúde dos serviços.
     */
    public void startMonitoring() {

        if (!NODE_ID.equals("node01"))
            return;

        Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    Health h = HealthClient.getHealthCheck().processHealth();

                    if (h != null) {
                        PaymentsClient.setStatus(h);
                        NodeClient nc = new NodeClient();
                        nc.processHealth(h);
                    }
                    Thread.sleep(Duration.ofSeconds(5));
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "Erro na guarda de health.");
                }
            }
        });

    }

    /**
     * Processa a fila de pagamento.
     */
    public void startPayment() {

        logger.info(">>>---> iniciando processador.");

        for (int i = 0; i < PAYMENT_WORKERS; i++) {
            Thread.ofVirtual().name("Worker - " + i).start(() -> {
                logger.info(">>>---> iniciando guarda de processamento de pagamento.");

                ArrayBlockingQueue<byte[]> fila = new ArrayBlockingQueue<>(100);
                dist.add(fila);

                while (true) {
                    try {
                        PaymentsClient.getInstance().processPayment(fila.take());
                    } catch (InterruptedException e) {
                        logger.log(Level.WARNING, "Erro no processamento da guarda de pagamento - {0}",
                                e.getMessage());
                        e.printStackTrace();
                    }
                }

            });
        }

        Thread.ofVirtual().start(() ->
        {
            try {
                Thread.sleep(200);
                logger.info(">>>---> iniciando distribuidor.");
                
                while (true) {
                    for (ArrayBlockingQueue<byte[]> x : dist) {
                        try {
                            x.offer(processamento.take());
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            } catch (InterruptedException ex) {
            }
        });
    }

    /**
     * Ativa o servidor HTTP.
     * 
     * @throws IOException
     */
    public void start() throws IOException {
        Undertow server = Undertow.builder()
                .addHttpListener(Service.HTTP_PORT, "0.0.0.0")
                .setHandler(new com.r13a.devpr2025.service.PathHandler().getHandlers())
                .setIoThreads(2)
                .setWorkerThreads(8)
                .setDirectBuffers(true)
                .setBufferSize(256)
                .build();

        server.start();

        logger.log(Level.INFO, "Servidor HTTP - Iniciado na porta: {0}", Service.HTTP_PORT);
    }

    public void stop() {
        if (serverHttp != null) {
            serverHttp.stop(Service.HTTP_PORT);
        }
    }

    private static void loadEnvVar() throws IOException {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = Service.class.getResourceAsStream("/version.fingerprint");
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n"); // Append newline character
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        logger.log(Level.INFO, ">>>---> Versao da aplicacao: {0}", content.toString());
        logger.log(Level.INFO, ">>>---> Max Threads: {0}", Service.MAX_THREAD);
        logger.log(Level.INFO, ">>>---> Porta do servidor: {0}", Service.HTTP_PORT);

        String nodeId = System.getenv("NODE_ID");
        if (nodeId != null) {
            NODE_ID = nodeId;
        } else {
            NODE_ID = "node01";
            logger.log(Level.SEVERE, "Nao informado o id do NO");
        }
        logger.log(Level.INFO, ">>>---> NODE_ID: {0}", NODE_ID);

        String defaultURL = System.getenv("PAYMENT_PROCESSOR_DEFAULT");
        if (defaultURL != null) {
            Service.urlA = defaultURL;
        } else {
            Service.urlA = "http://localhost:8001";
        }
        logger.log(Level.INFO, ">>>---> Health URL A: {0}", Service.urlA);

        String fallbackURL = System.getenv("PAYMENT_PROCESSOR_FALLBACK");
        if (fallbackURL != null) {
            Service.urlB = fallbackURL;
        } else {
            Service.urlB = "http://localhost:8002";
        }
        logger.log(Level.INFO, ">>>---> Health URL B: {0}", Service.urlB);

        String pair = System.getenv("PAIR_URL");
        if (pair != null) {
            Service.pairURL = pair;
        } else {
            pairURL = "http://" + (Service.NODE_ID.equals("node02") ? "node01" : "node02") + ":" + Service.HTTP_PORT;
        }
        logger.log(java.util.logging.Level.INFO, ">>>---> pairURL: {0}", Service.pairURL);

        String node = System.getenv("NODE_URL");
        if (node != null) {
            Service.nodeURL = node;
        } else {
            Service.nodeURL = "http://node02:" + Service.HTTP_PORT;
        }
        logger.log(java.util.logging.Level.INFO, ">>>---> nodeURL: {0}", Service.nodeURL);
    }
}
