package com.r13a.devpr2025.service;

import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r13a.devpr2025.Service;
import com.r13a.devpr2025.client.NodeClient;
import com.r13a.devpr2025.entity.Total;
import com.sun.net.httpserver.HttpExchange;




public class Summary {

    private static final Logger logger = Logger.getLogger(Summary.class.getName());
    // Resposta do processamento do par.
    private Total totalPar;

    private static final ObjectMapper mapa = new ObjectMapper();


    public Total calculate(Instant from, Instant to) throws Exception {

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
     * Gera resposta ao pedido dedados do resumo.
     * @param total Totais a ser respondido.
     * @param exchange
     * @throws Exception 
     */
    public void retorna(Total total, HttpExchange exchange) throws Exception {

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

        //logger.log(Level.INFO, ">>>---> Summary: {0}", bodyString.toString());

        exchange.sendResponseHeaders(200, bodyString.length());
        OutputStream os = exchange.getResponseBody();
        os.write(bodyString.toString().getBytes());
        os.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Problemas no processamento da resposta do relatorio - Mensagem: {0} - Classe: {1}", new Object[] {e.getMessage(), e.getClass().toString()});
            throw new Exception(e);
        }

    }

    /**
     * Retorna o sumario do servidor local.
     */
    public void data(HttpExchange exchange) throws Exception {
        Map<String, String> params = getParamMap(exchange.getRequestURI().getQuery());

        Instant from = params.get("from") == null ? Instant.now() : Instant.parse(params.get("from"));
        Instant to = params.get("to") == null ? Instant.now() : Instant.parse(params.get("to"));
        //logger.log(Level.INFO, ">>>---> from: {0}", from.toString());
        //logger.log(Level.INFO, ">>>---> to: {0}", to.toString());

        Thread.sleep(Duration.ofMillis(700));

        Total total = calculate(from, to);
        ObjectMapper mapa = new ObjectMapper();
        String bodyString = mapa.writeValueAsString(total);        
        exchange.sendResponseHeaders(200, bodyString.length());
        OutputStream os = exchange.getResponseBody();
        os.write(bodyString.getBytes());
        os.close();
    }

    /**
     * Processa o total geral processado.
     */
    public void process(HttpExchange exchange) throws Exception {
        Map<String, String> params = getParamMap(exchange.getRequestURI().getQuery());

        Instant from = params.get("from") == null ? Instant.now() : Instant.parse(params.get("from"));
        Instant to = params.get("to") == null ? Instant.now() : Instant.parse(params.get("to"));
        //logger.log(Level.INFO, ">>>---> from: {0}", from.toString());
        //logger.log(Level.INFO, ">>>---> to: {0}", to.toString());


        Thread t = Thread.ofVirtual().start(() -> {
            //logger.info(">>>---> pedindo resumo do par");

            NodeClient nc = new NodeClient();
            totalPar = nc.requestSummary(from, to);

        });
        Thread.sleep(Duration.ofMillis(700));
        Total total = calculate(from, to);

        // Espera resposta do par.
        while (t.isAlive()) {}

        //logger.log(Level.INFO, "Dados Locais: {0}", mapa.writeValueAsString(total));
        //logger.log(Level.INFO, "Dados Par   : {0}", mapa.writeValueAsString(totalPar));


        if (totalPar != null) {
            total.totalDefault += totalPar.totalDefault;
            total.somaDefault += totalPar.somaDefault;
            total.totalFallback += totalPar.totalFallback;
            total.somaFallback += totalPar.somaFallback;
        }

        retorna(total, exchange);

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
}
