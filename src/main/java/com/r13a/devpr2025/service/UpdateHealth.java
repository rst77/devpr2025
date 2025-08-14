package com.r13a.devpr2025.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.r13a.devpr2025.client.PaymentsClient;
import com.r13a.devpr2025.entity.Health;
import com.sun.net.httpserver.HttpExchange;

public class UpdateHealth {

    private static final Logger logger = Logger.getLogger(UpdateHealth.class.getName());

    public void process(HttpExchange exchange) throws Exception {
        InputStream is = exchange.getRequestBody();
        byte[] bytes = is.readAllBytes();

        ObjectMapper mapa = new ObjectMapper();
        mapa.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        try {
            //logger.info("Atualizando Status");
            Health h = mapa.readValue(new String(bytes, StandardCharsets.UTF_8), Health.class);

            PaymentsClient.setStatus(h);

            exchange.sendResponseHeaders(200, 0);
            exchange.close();

        } catch (IOException e) {
            logger.log(Level.WARNING, "Problema no processamento do pagamento - {0}", e.getMessage());
            exchange.sendResponseHeaders(500, 0);
            exchange.close();

        }

    }

}
