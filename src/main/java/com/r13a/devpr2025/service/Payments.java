package com.r13a.devpr2025.service;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class Payments implements HttpHandler {

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        exchange.getRequestReceiver().receiveFullBytes((ex, data) -> {
            exchange.setStatusCode(202);
            exchange.endExchange();

            Service.processamento.offer(data);

        });
    }

    /*
     * public void process(HttpExchange exchange) throws Exception {
     * 
     * try {
     * InputStream requestBodyStream = exchange.getRequestBody();
     * BufferedReader reader = new BufferedReader(new
     * InputStreamReader(requestBodyStream, "UTF-8"));
     * StringBuilder bodyContent = new StringBuilder();
     * String line;
     * while ((line = reader.readLine()) != null) {
     * bodyContent.append(line);
     * }
     * String body = bodyContent.toString();
     * 
     * Map<String, String> map = new HashMap<>();
     * 
     * Payment p = new Payment();
     * 
     * int cont = 1;
     * for (String keyValue : body.split(",")) {
     * String[] data = keyValue.split(":");
     * 
     * if (cont == 1)
     * p.setCorrelationId(data[1].replace("\"", "").trim());
     * else
     * p.setAmount(Double.parseDouble(data[1].replace("\"", "").replace("}",
     * "").trim()));
     * 
     * cont++;
     * }
     * 
     * Service.processamento.add(p);
     * 
     * exchange.sendResponseHeaders(200, 0);
     * exchange.close();
     * 
     * } catch (IOException e) {
     * logger.log(Level.WARNING, "Problema no processamento do pagamento - {0}",
     * e.getMessage());
     * e.printStackTrace();
     * }
     * }
     */
}
