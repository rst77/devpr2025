package com.r13a.rinha2025.service;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class Shutdown implements HttpHandler {

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        System.out.println("Derrubando aplicacao");
        System.exit(0);

    }

}
