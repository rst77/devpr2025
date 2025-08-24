package com.r13a.rinha2025.client;

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
import com.r13a.rinha2025.Service;
import com.r13a.rinha2025.entity.Health;
import com.r13a.rinha2025.entity.Total;

public class NodeClient {
    private static final Logger logger = Logger.getLogger(NodeClient.class.getName());

    private static final ObjectMapper mapa = new ObjectMapper();

    public void processHealth(Health h) {

        try {

            HttpRequest.BodyPublisher body = BodyPublishers.ofString(mapa.writeValueAsString(h));

            HttpClient client = HttpClient.newBuilder()
                    .build();

            for (int i = 0; i < Service.PAIR_NBR; i++) {

                if ((i + 1) == Service.NODE_ID)
                    continue;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(
                                Service.NODE_URL + (i + 1) + ":" + Service.HTTP_PORT + "/update-health"))
                        .timeout(Duration.ofMillis(4000))
                        .header("Content-Type", "application/json")
                        .POST(body)
                        .build();

                HttpResponse<String> resp = client.send(request, BodyHandlers.ofString());

                if (resp.statusCode() != 200)
                    logger.log(java.util.logging.Level.WARNING,
                            ">>>---> Problemas na atualizacao do Health - Par: {0} - Codigo retorno: {1}",
                            new Object[] { (i + 1), resp.statusCode() });

                client.close();
            }
        } catch (IOException | InterruptedException ex) {
            // So vai
        }
    }

    public Total[] requestSummary(Instant from, Instant to) {
        Total[] retorno = new Total[Service.PAIR_NBR];

        String query = "/payments-data?from={0}&to={1}";

        Object[] parameters = { from.toString(), to.toString() };
        String queryString = MessageFormat.format(query, parameters);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(10))
                .build();

        for (int i = 0; i < Service.PAIR_NBR; i++) {
            try {

                if ((i + 1) == Service.NODE_ID)
                    continue;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(Service.NODE_URL + (i + 1) + ":" + Service.HTTP_PORT + queryString))
                        .timeout(Duration.ofMillis(4000))
                        .header("Content-Type", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> resp = client.send(request, BodyHandlers.ofString());

                if (resp.statusCode() == 200) {

                    ObjectMapper mapa = new ObjectMapper();
                    Total total = mapa.readValue(resp.body(), Total.class);
                    retorno[i] = total;

                } else {
                    // Segue a vida.
                }
            } catch (IOException | InterruptedException ex) {
                // Bola em jogo.

            }
        }
        client.close();

        return retorno;
    }

}
