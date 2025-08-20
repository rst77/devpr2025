package com.r13a.rinha2025.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r13a.rinha2025.Service;
import com.r13a.rinha2025.client.NodeClient;
import com.r13a.rinha2025.entity.Total;

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

    public void limpa(Instant to) {

        long cap = to.minusSeconds(10).toEpochMilli();

        Instant agora = Instant.now();
        System.out.println("Antes A: " + Service.resultadoA.size());
        Service.resultadoA.removeIf(e -> e.getRequestedAt() < cap);
        System.out.println("Depois A: " + Service.resultadoA.size() + " - Tempo: " + Instant.now().minusMillis(agora.toEpochMilli()).toEpochMilli());

        agora = Instant.now();
        System.out.println("Antes B: " + Service.resultadoB.size());
        Service.resultadoB.removeIf(e -> e.getRequestedAt() < cap);
        System.out.println("Depois B: " + Service.resultadoB.size() + " - Tempo: " + Instant.now().minusMillis(agora.toEpochMilli()).toEpochMilli());

    }

    public Total calculate(Instant from, Instant to) throws Exception {

        Total total = new Total();
        Service.resultadoA
                .stream()
                .filter(e -> e.getRequestedAt() >= from.toEpochMilli() &&
                        e.getRequestedAt() <= to.toEpochMilli())
                .forEach((e) -> {
                    total.totalDefault++;
                    total.somaDefault += e.getAmount();
                });

        Service.resultadoB
                .stream()
                .filter(e -> e.getRequestedAt() >= from.toEpochMilli() &&
                        e.getRequestedAt() <= to.toEpochMilli())
                .forEach((e) -> {
                    total.totalFallback++;
                    total.somaFallback += e.getAmount();
                });

        return total;
    }

    /**
     * Gera resposta ao pedido dedados do resumo.
     * 
     * @param total    Totais a ser respondido.
     * @param exchange
     * @throws Exception
     */
    public String retorna(Total total, HttpServerExchange exchange) throws Exception {

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

            return bodyString.toString();

        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "Problemas no processamento da resposta do relatorio - Mensagem: {0} - Classe: {1}",
                    new Object[] { e.getMessage(), e.getClass().toString() });
            throw new Exception(e);
        }
    }

    /**
     * Retorna o sumario do servidor local.
     */
    public String data(HttpServerExchange exchange) throws Exception {


        OffsetDateTime sFrom = null;
        OffsetDateTime sTo = null;

        if (exchange.getQueryParameters().isEmpty()) {
            logger.warning(">>>---> erro nos dados de captura de dados");
            System.exit(-1);
        } else {
            sFrom = OffsetDateTime.parse(exchange.getQueryParameters().get("from").getFirst());
            sTo = OffsetDateTime.parse(exchange.getQueryParameters().get("to").getFirst());
        }            

    
        Instant from = (sFrom == null) ? Instant.now() : sFrom.toInstant();
        Instant to = (sTo == null) ? Instant.now() : sTo.toInstant();

        Thread.sleep(Duration.ofMillis(Service.SUMM_DELAY));

        Total total = calculate(from, to);
        ObjectMapper mapa = new ObjectMapper();

        Thread.ofVirtual().start(() -> {
                limpa(to);
        });

        return mapa.writeValueAsString(total);
    }

    /**
     * Processa o total geral processado.
     */
    public String process(HttpServerExchange exchange) throws Exception {


        OffsetDateTime sFrom = null;
        OffsetDateTime sTo = null;

        if (exchange.getQueryParameters().isEmpty()) {
            sFrom = OffsetDateTime.now().minusMinutes(5);
            sTo = OffsetDateTime.now();
        } else {
            sFrom = OffsetDateTime.parse(exchange.getQueryParameters().get("from").getFirst());
            sTo = OffsetDateTime.parse(exchange.getQueryParameters().get("to").getFirst());
        }            

        Instant from = (sFrom == null) ? Instant.now() : sFrom.toInstant();
        Instant to = (sTo == null) ? Instant.now() : sTo.toInstant();

        NodeClient nc = new NodeClient();
        totalPar = nc.requestSummary(from, to);

        Total total = calculate(from, to);

        logger.log(Level.INFO, "Dados Locais: {0}", mapa.writeValueAsString(total));
        logger.log(Level.INFO, "Dados Par   : {0}", mapa.writeValueAsString(totalPar));

        if (totalPar != null) {
            total.totalDefault += totalPar.totalDefault;
            total.somaDefault += totalPar.somaDefault;
            total.totalFallback += totalPar.totalFallback;
            total.somaFallback += totalPar.somaFallback;
        }

        Thread.ofVirtual().start(() -> {
                limpa(to);
        });

        return retorna(total, exchange);
    }

    /**
     * Extrai os valores da query string.
     */
    public Map<String, String> getParamMap(String query) {
        if (query == null || query.isEmpty())
            return Collections.emptyMap();

        return Stream.of(query.split("&"))
                .filter(s -> !s.isEmpty())
                .map(kv -> kv.split("=", 2))
                .collect(Collectors.toMap(x -> x[0], x -> x[1]));

    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
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
                            .wrap(data(exchange).getBytes(StandardCharsets.UTF_8)));

        }
    }
}
