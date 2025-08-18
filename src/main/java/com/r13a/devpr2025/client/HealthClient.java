package com.r13a.devpr2025.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.r13a.devpr2025.Service;
import com.r13a.devpr2025.entity.Health;

public class HealthClient {

    private static final Logger logger = Logger.getLogger(PaymentsClient.class.getName());


    // Valores de controle do comportamento.
    private boolean ativoA;
    private boolean ativoB;
    private int reqTimeoutA = 100;
    private int reqTimeoutB = 100;
    HttpRequest.BodyPublisher body;

    private static HealthClient hc;

    public static HealthClient getHealthCheck() {
        if (hc == null) 
            hc = new HealthClient();

        return hc;

    }

    public Health processHealth() {

            HttpClient clientA = HttpClient.newBuilder()
                    .build();

            HttpRequest requestA = HttpRequest.newBuilder()
                    .uri(URI.create(Service.urlA + "/payments/service-health"))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpClient clientB = HttpClient.newBuilder()
                    .build();

            HttpRequest requestB = HttpRequest.newBuilder()
                    .uri(URI.create(Service.urlB + "/payments/service-health"))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            try {

                HttpResponse<String> respA = clientA.send(requestA, BodyHandlers.ofString());
                record Status(boolean failing, int minResponseTime) {
                }
                ObjectMapper mapa = new ObjectMapper();
                mapa.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
                Status registroA = null;
                Status registroB = null;

                if (respA.statusCode() == 200) {
                    registroA = mapa.readValue(respA.body(), Status.class);

                    ativoA = !registroA.failing;
                    reqTimeoutA = registroA.minResponseTime < 100 ? 100 : registroA.minResponseTime;

                }

                HttpResponse<String> respB = clientB.send(requestB, BodyHandlers.ofString());
                if (respB.statusCode() == 200) {
                    registroB = mapa.readValue(respB.body(), Status.class);

                    ativoB = !registroB.failing;
                    reqTimeoutB = registroB.minResponseTime < 100 ? 100 : registroB.minResponseTime;

                }
                Health h = new Health(ativoB, reqTimeoutB, ativoA, reqTimeoutA);
                return h;
            } catch (IOException | InterruptedException e) {
                logger.log(Level.WARNING, ">>>---> Erro no processamento do health: {0}", e.getMessage());
            }

        return null;

    }

}
