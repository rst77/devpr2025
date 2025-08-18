package com.r13a.devpr2025.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r13a.devpr2025.Service;
import com.r13a.devpr2025.entity.Health;
import com.r13a.devpr2025.entity.Total;

public class NodeClient {
    private static final Logger logger = Logger.getLogger(NodeClient.class.getName());


    private static final ObjectMapper mapa = new ObjectMapper();

    public void processHealth(Health h) {

        try {

            HttpRequest.BodyPublisher body = BodyPublishers.ofString(mapa.writeValueAsString(h));

            HttpClient client = HttpClient.newBuilder()
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(Service.nodeURL + "/update-health"))
                    .header("Content-Type", "application/json")
                    .POST(body)
                    .build();

            HttpResponse<String> resp = client.send(request, BodyHandlers.ofString());

            if (resp.statusCode() != 200)
                logger.log(java.util.logging.Level.WARNING,
                        ">>>---> Problemas na atualizacao do Health - Codigo retorno: {0}", resp.statusCode());

            client.close();
        } catch (IOException | InterruptedException ex) {
            // So vai
        }
    }

    public Total requestSummary(Instant from, Instant to) {

        try {

            String query = "/payments-data?from={0}&to={1}";

            Object[] parameters = { from.toString(), to.toString() };
            String queryString = MessageFormat.format(query, parameters);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(Service.pairURL + queryString))
                    .timeout(Duration.ofMillis(4000))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(request, BodyHandlers.ofString());

            if (resp.statusCode() == 200) {

                ObjectMapper mapa = new ObjectMapper();
                Total total = mapa.readValue(resp.body(), Total.class);
                return total;

            } else {
                // Segue a vida.
            }
            client.close();
        } catch (IOException | InterruptedException ex) {
            // Bola em jogo.
        }

        return null;
    }

}
