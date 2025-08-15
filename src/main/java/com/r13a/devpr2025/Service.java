package com.r13a.devpr2025;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.r13a.devpr2025.client.HealthClient;
import com.r13a.devpr2025.client.NodeClient;
import com.r13a.devpr2025.client.PaymentsClient;
import com.r13a.devpr2025.entity.Health;
import com.r13a.devpr2025.entity.Payment;
import com.r13a.devpr2025.service.Payments;
import com.r13a.devpr2025.service.Summary;
import com.r13a.devpr2025.service.UpdateHealth;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Service {

    private static final Logger logger = Logger.getLogger(Service.class.getName());

    private final static int HTTP_PORT = 9901;
    private final static int MAX_THREAD = 900;
    public final static int DELETE_CAP = 5;
    public final static int SUMM_DELAY = 800;
    public final static int THREAD_DELAY = 1200;

    public static String NODE_ID;

    public final static Stack<Payment> processamento = new Stack<>();
    public final static Stack<Payment> rebote = new Stack<>();
    public static final Map<Long, Payment> resultado = new HashMap<>();

    public static String urlA = null;
    public static String urlB = null;

    public static String pairURL;
    public static String nodeURL;

    private HttpServer serverHttp;

    public static void main(String[] args) throws Exception {
        logger.log(Level.INFO, ">>>---> Versão da aplicação: {0}", Instant.now().toString());
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

        logger.info(">>>---> iniciando guarda de monitoracao.");

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

        for (int i = 0; i < 4; i++)
            Thread.ofVirtual().start(() -> {
                logger.info(">>>---> iniciando guarda de processamento de pagamento.");
                while (true) {
                    if (!processamento.empty() && PaymentsClient.isAlmostReady() ) {
                        while (!processamento.empty() && Thread.activeCount() < Service.MAX_THREAD) {
                            try {
                                Payment pd = processamento.pop();
                                //Thread.ofVirtual().start(() -> {
                                    PaymentsClient pc = new PaymentsClient();
                                    pc.processPayment(pd);
                                //});
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Erro no processamento da guarda de pagamento - {0}",
                                        e.getMessage());
                            }
                        }
                    }
                    try {
                        Thread.sleep(Duration.ofMillis(Service.THREAD_DELAY));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }
            });

        /**
         * Rebote da preferencia para o processador A
         */
        Thread.ofVirtual().start(() -> {
            logger.info(">>>---> iniciando guarda de processamento de rebote.");
            while (true) {
                if (!rebote.empty() && PaymentsClient.isAReady()) {
                    while (!rebote.empty() && Thread.activeCount() < Service.MAX_THREAD + 100) {
                        try {
                            Payment pd = rebote.pop();
                            //Thread.ofVirtual().start(() -> {
                                PaymentsClient pc = new PaymentsClient();
                                pc.processPayment(pd);
                            //});

                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Erro no processamento da guarda de rebote - {0}",
                                    e.getMessage());
                        }
                    }
                    try {
                        Thread.sleep(Duration.ofMillis(1000));
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        });

    }

    /**
     * Ativa o servidor HTTP.
     * 
     * @throws IOException
     */
    public void start() throws IOException {
        serverHttp = HttpServer.create(new InetSocketAddress(Service.HTTP_PORT), 0);
        serverHttp.createContext("/", new Router());
        serverHttp.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        serverHttp.start();
        logger.log(Level.INFO, "Servidor HTTP - Iniciado na porta: {0}", Service.HTTP_PORT);
    }

    public void stop() {
        if (serverHttp != null) {
            serverHttp.stop(Service.HTTP_PORT);
        }
    }

    static class Router implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod()) &&
                    exchange.getRequestURI().getPath().equals("/payments-summary")) {
                Thread.ofVirtual().start(() -> {
                    try {
                        Summary s = new Summary();
                        s.process(exchange);
                    } catch (Exception e) {
                        logger.log(Level.INFO, ">>>---> problema processamento relatorio - {0}", e.getMessage());
                    }
                });
            } else if ("GET".equals(exchange.getRequestMethod()) &&
                    exchange.getRequestURI().getPath().equals("/payments-data")) {
                Thread.ofVirtual().start(() -> {
                    try {
                        Summary s = new Summary();
                        s.data(exchange);
                    } catch (Exception e) {
                        logger.log(Level.INFO, ">>>---> problema para obter parte dos relatorio - {0}",
                                e.getMessage());
                    }
                });
            } else if ("POST".equals(exchange.getRequestMethod()) &&
                    exchange.getRequestURI().getPath().equals("/update-health")) {
                Thread.ofVirtual().start(() -> {
                    try {
                        UpdateHealth uh = new UpdateHealth();
                        uh.process(exchange);
                    } catch (Exception e) {
                        logger.log(Level.INFO, ">>>---> problema processamento relatorio - {0}", e.getMessage());
                    }
                });

            } else if ("POST".equals(exchange.getRequestMethod()) &&
                    exchange.getRequestURI().getPath().equals("/payments")) {

                //Thread.ofVirtual().start(() -> {
                    try {
                        Payments p = new Payments();
                        p.process(exchange);
                    } catch (Exception e) {
                        logger.log(Level.INFO, ">>>---> problema processamento relatorio - {0}", e.getMessage());
                    }
               // });

            } else {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
            }
        }

    }

}
