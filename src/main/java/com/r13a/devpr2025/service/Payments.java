package com.r13a.devpr2025.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.r13a.devpr2025.Service;
import com.r13a.devpr2025.entity.Payment;
import com.sun.net.httpserver.HttpExchange;

public class Payments {

    private static final Logger logger = Logger.getLogger(Payments.class.getName());

    Pattern cp = Pattern.compile("\"correlationId\"\s*:\s*(.*?)");
    Pattern ap = Pattern.compile("\"amount\"\\s*:\\s*(.*?)");

    public void process(HttpExchange exchange) throws Exception {

        try {
        InputStream requestBodyStream = exchange.getRequestBody();
           BufferedReader reader = new BufferedReader(new InputStreamReader(requestBodyStream, "UTF-8"));
            StringBuilder bodyContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                bodyContent.append(line);
            }
            String body = bodyContent.toString();

            Map<String, String> map = new HashMap<>();

            Payment p = new Payment();

            int cont = 1;
            for (String keyValue: body.split(",")) {
                String[] data = keyValue.split(":");

                if (cont == 1) 
                    p.setCorrelationId( data[1].replace("\"", "").trim() );
                else
                    p.setAmount( Double.parseDouble( data[1].replace("\"", "").replace("}", "").trim() ) );

                cont++;
            }            

            Service.processamento.add(p);

            exchange.sendResponseHeaders(200, 0);
            exchange.close();

        } catch (IOException e) {
            logger.log(Level.WARNING, "Problema no processamento do pagamento - {0}", e.getMessage());
            e.printStackTrace();
        }
    }
}
