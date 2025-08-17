package com.r13a.devpr2025.service;

import java.io.IOException;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r13a.devpr2025.client.PaymentsClient;
import com.r13a.devpr2025.entity.Health;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class UpdateHealth implements HttpHandler {

    private static final Logger logger = Logger.getLogger(UpdateHealth.class.getName());
    private static final ObjectMapper mapa = new ObjectMapper();

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        exchange.getRequestReceiver().receiveFullBytes((ex, data) -> {
            exchange.setStatusCode(200);
            exchange.getResponseSender().send("");

            try {

                Health h = mapa.readValue(data, Health.class);
                PaymentsClient.setStatus(h);

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        });
    }


}
