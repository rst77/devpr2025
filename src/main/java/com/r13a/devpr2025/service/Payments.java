package com.r13a.devpr2025.service;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.r13a.devpr2025.Service;
import com.sun.net.httpserver.HttpExchange;

public class Payments {

    private static final Logger logger = Logger.getLogger(Payments.class.getName());

    public void process(HttpExchange exchange) throws Exception {

        try {
            Service.processamento.add(exchange.getRequestBody().readAllBytes());

            exchange.sendResponseHeaders(200, 0);
            exchange.close();

        } catch (IOException e) {
            logger.log(Level.WARNING, "Problema no processamento do pagamento - {0}", e.getMessage());
            e.printStackTrace();
        }
    }
}
