package com.r13a.rinha2025.service;

import java.util.logging.Logger;

import com.r13a.rinha2025.Service;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class Payments  implements HttpHandler {

    private static final Logger logger = Logger.getLogger(Payments.class.getName());

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        exchange.getRequestReceiver().receiveFullBytes((ex, data) -> {
            exchange.setStatusCode(202);
            exchange.endExchange();
            Service.processamento.offer(data);
        });
    }

}
