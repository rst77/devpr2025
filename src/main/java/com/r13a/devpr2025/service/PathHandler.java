package com.r13a.devpr2025.service;

import io.undertow.server.HttpHandler;

public class PathHandler {

    private final HttpHandler handlers;

    public PathHandler() {
        this.handlers = configureHandlers();
    }

    private HttpHandler configureHandlers() {
        return new io.undertow.server.handlers.PathHandler()
                .addExactPath("/payments", new Payments())
                .addExactPath("/payments-summary", new Summary())
                .addExactPath("/payments-data", new Summary())
                .addExactPath("/update-health", new UpdateHealth());
    }

    public HttpHandler getHandlers() {
        return handlers;
    }
}
