package com.r13a.rinha2025;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.r13a.rinha2025.client.HealthClient;
import com.r13a.rinha2025.client.NodeClient;
import com.r13a.rinha2025.client.PaymentsClient;
import com.r13a.rinha2025.entity.Health;
import com.r13a.rinha2025.entity.Payment;
import com.r13a.rinha2025.service.Payments;
import com.r13a.rinha2025.service.Summary;
import com.r13a.rinha2025.service.UpdateHealth;
import com.sun.net.httpserver.HttpServer;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;

public class Service {

    private static final Logger logger = Logger.getLogger(Service.class.getName());

    private final static int HTTP_PORT = 9901;
    private final static int MAX_THREAD = 900;
    public final static int DELETE_CAP = 5;

    public static int SUMM_DELAY = 500;
    public static int PAYMENT_PROCESSORS = 20;
    public static int CONN_TO = 100;
    public static int REQ_TO = 15;

    public static String NODE_ID;

    public final static LinkedTransferQueue<byte[]> processamento = new LinkedTransferQueue<>();
    public final static List<ArrayBlockingQueue<byte[]>> dist = new ArrayList<>();

    public final static LinkedTransferQueue<Payment> resultadoA = new LinkedTransferQueue<>();
    public final static LinkedTransferQueue<Payment> resultadoB = new LinkedTransferQueue<>();

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

        logger.info(">>>---> iniciando guarda de health.");

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
        int pwID = 0;
        for (; pwID < PAYMENT_PROCESSORS; pwID++) {
            Thread.ofVirtual().name("P" + pwID).start(() -> {

                ArrayBlockingQueue<byte[]> fila = new ArrayBlockingQueue<>(1000);
                dist.add(fila);
                PaymentsClient pc = new PaymentsClient();

                logger.log(Level.INFO, ">>>---> iniciando guarda de pagamento - {0}", Thread.currentThread().getName());

                while (true) {

                    try {
                        // if (pc.isFullReady())
                        pc.processPayment(processamento.take(), (byte) 0);

                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Erro no processamento da guarda de pagamento - {0}",
                                e.getMessage());
                    }

                }
            });
        }

        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(200);
                logger.info(">>>---> iniciando distribuidor.");

                while (true) {
                    for (ArrayBlockingQueue<byte[]> x : dist) {
                        try {
                            x.offer(processamento.take());
                        } catch (InterruptedException e) {
                            logger.log(Level.WARNING, "Erro no processamento da guarda de distribuicao - {0}",
                                    e.getMessage());
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
                .setHandler(getHandleRequest())
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

        public HttpHandler getHandleRequest() {
            return new io.undertow.server.handlers.PathHandler()
                    .addExactPath("/payments", new Payments())
                    .addExactPath("/payments-summary", new Summary())
                    .addExactPath("/payments-data", new Summary())
                    .addExactPath("/update-health", new UpdateHealth());
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

        String conn_to = System.getenv("CONN_TO");
        if (conn_to != null) {
            Service.CONN_TO = Integer.parseInt(conn_to);
        }
        logger.log(java.util.logging.Level.INFO, ">>>---> CONN_TO: {0}", Service.CONN_TO);

        String req_to = System.getenv("REQ_TO");
        if (req_to != null) {
            Service.CONN_TO = Integer.parseInt(req_to);
        }
        logger.log(java.util.logging.Level.INFO, ">>>---> REQ_TO: {0}", Service.REQ_TO);

        String pp = System.getenv("PAYMENT_PROCESSORS");
        if (pp != null) {
            Service.PAYMENT_PROCESSORS = Integer.parseInt(pp);
        }
        logger.log(java.util.logging.Level.INFO, ">>>---> PAYMENT_PROCESSORS: {0}", Service.PAYMENT_PROCESSORS);

        String sd = System.getenv("SUMM_DELAY");
        if (sd != null) {
            Service.SUMM_DELAY = Integer.parseInt(sd);
        }
        logger.log(java.util.logging.Level.INFO, ">>>---> SUMM_DELAY: {0}", Service.SUMM_DELAY);

    }
}
