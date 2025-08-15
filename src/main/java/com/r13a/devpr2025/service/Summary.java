package com.r13a.devpr2025.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.r13a.devpr2025.Service;
import com.r13a.devpr2025.client.NodeClient;
import com.r13a.devpr2025.entity.Total;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

public class Summary implements HttpHandler {

    private static final String CONTENT_TYPE = "application/json";
    private static final HttpString CONTENT_TYPE_HEADER = new HttpString("Content-Type");

    private static final Logger logger = Logger.getLogger(Summary.class.getName());

    // Resposta do processamento do par.
    private Total totalPar;

    private static final ObjectMapper mapa = new ObjectMapper();

    @Override
    public void handleRequest(HttpServerExchange exchange) {

        if (exchange.getRequestPath().equals("/payments-summary")) {

            exchange.setStatusCode(200);
            exchange.getResponseHeaders().put(CONTENT_TYPE_HEADER, CONTENT_TYPE);
            exchange.getResponseSender()
                    .send(ByteBuffer
                            .wrap(process(exchange).getBytes(StandardCharsets.UTF_8)));

        } else if (exchange.getRequestPath().equals("/payments-data")) {

            exchange.setStatusCode(200);
            exchange.getResponseHeaders().put(CONTENT_TYPE_HEADER, CONTENT_TYPE);
            exchange.getResponseSender()
                    .send(ByteBuffer
                            .wrap(process(exchange).getBytes(StandardCharsets.UTF_8)));
            data(exchange);

        }

    }

    public void limpa(Instant to) {
        long cap = to.minusSeconds(Service.SUMM_DELAY).toEpochMilli();
        Service.resultado.entrySet().removeIf(e -> e.getKey() < cap);
    }

    public Total calculate(Instant from, Instant to) {

        Total total = new Total();
        Service.resultado
                .entrySet()
                .stream()
                .filter(e -> e.getKey() >= from.toEpochMilli() &&
                        e.getKey() <= to.toEpochMilli())
                .forEach((e) -> {
                    if (e.getValue().getService() == 1) {
                        total.totalDefault++;
                        total.somaDefault += e.getValue().getAmount();
                    } else {
                        total.totalFallback++;
                        total.somaFallback += e.getValue().getAmount();
                    }

                });
        return total;
    }

    /**
     * Retorna o sumario do servidor local.
     */
    public String data(HttpServerExchange exchange) {

        String sFrom = exchange.getQueryParameters().get("from").getFirst();
        String sTo = exchange.getQueryParameters().get("to").getFirst();

        Instant from = (sFrom == null) ? Instant.now() : Instant.parse(sFrom);
        Instant to = (sTo == null) ? Instant.now() : Instant.parse(sTo);

        try {
            Thread.sleep(Duration.ofMillis(Service.SUMM_DELAY));

            Total total = calculate(from, to);
            ObjectMapper mapa = new ObjectMapper();

            return mapa.writeValueAsString(total);
        } catch (JsonProcessingException | InterruptedException e) {
            e.printStackTrace();
        }

        return null;

    }

    /**
     * Processa o total geral processado.
     */
    public String process(HttpServerExchange exchange) {
        try {
            String sFrom = exchange.getQueryParameters().get("from").getFirst();
            String sTo = exchange.getQueryParameters().get("to").getFirst();

            Total total = new Total();

            if (sFrom != null && sTo != null) {

                Instant from = Instant.parse(sFrom);
                Instant to = Instant.parse(sTo);

                // Requisita dados do par.
                Thread t = Thread.ofVirtual().start(() -> {
                    NodeClient nc = new NodeClient();
                    totalPar = nc.requestSummary(from, to);
                });

                try {
                    // Espera a completude de requisições ja executadas.
                    Thread.sleep(Duration.ofMillis(Service.SUMM_DELAY));
                } catch (InterruptedException ex) {
                }

                total = calculate(from, to);

                // Espera resposta do par.
                while (t.isAlive()) {
                }

                logger.log(Level.INFO, "Dados Locais: {0}", total.toString());
                // Soma valores.
                if (totalPar != null) {
                    logger.log(Level.INFO, "Dados Par   : {0}", totalPar.toString());

                    total.totalDefault += totalPar.totalDefault;
                    total.somaDefault += totalPar.somaDefault;
                    total.totalFallback += totalPar.totalFallback;
                    total.somaFallback += totalPar.somaFallback;
                }
            }

            return retorna(total, exchange);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }

    /**
     * Gera resposta ao pedido dedados do resumo.
     * 
     * @param total    Totais a ser respondido.
     * @param exchange
     */
    public String retorna(Total total, HttpServerExchange exchange) {

        try {

            StringBuilder bodyString = new StringBuilder();
            bodyString.append("{ \"default\" : {\"totalRequests\": ");
            bodyString.append(total.totalDefault);
            bodyString.append(",\"totalAmount\": ");
            bodyString.append(total.somaDefault);
            bodyString.append("}, \"fallback\" : {\"totalRequests\": ");
            bodyString.append(total.totalFallback);
            bodyString.append(",\"totalAmount\": ");
            bodyString.append(total.somaFallback);
            bodyString.append("}}");
            System.out.println(bodyString.toString());
            return bodyString.toString();

        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "Problemas no processamento da resposta do relatorio - Mensagem: {0} - Classe: {1}",
                    new Object[] { e.getMessage(), e.getClass().toString() });
            e.printStackTrace();
        }
        return null;
    }

}
