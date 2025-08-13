package com.r13a.devpr2025.grpcservices;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.r13a.devpr2025.client.PaymentsClient;
import com.r13a.devpr2025.grpc.ControlData;
import com.r13a.devpr2025.grpc.PaymentData;
import com.r13a.devpr2025.grpc.PaymentList;
import com.r13a.devpr2025.grpc.PaymentServiceGrpc;
import com.r13a.devpr2025.grpc.PaymentServiceGrpc.PaymentServiceStub;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

public class PaymentsBackClient {

    public final static Stack<PaymentData> processamento = new Stack<>();
    public final static Stack<PaymentData> resultado = new Stack<>();
    public final static Stack<PaymentData> rebote = new Stack<>();

    private static final Logger logger = Logger.getLogger(PaymentsBackClient.class.getName());

    public static void main(String[] args) throws InterruptedException {
        logger.info(">>>---> Iniciando cliente");

        String defaultURL = System.getenv("LB_URL");
        if (defaultURL == null) {
            defaultURL = "localhost";
        }
        logger.info(">>>---> URL LD: " + defaultURL);

        final CountDownLatch done = new CountDownLatch(2);

        // Dados para conectar ao serivdor.
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(defaultURL, 5000)
                .usePlaintext()
                .build();

        PaymentServiceStub stub = PaymentServiceGrpc.newStub(channel);

        // Processaento dos dados que serão enviados pelo servidor.
        ClientResponseObserver<PaymentList, PaymentList> clientResponseObserver = new ClientResponseObserver<PaymentList, PaymentList>() {

            @Override
            public void beforeStart(final ClientCallStreamObserver<PaymentList> requestStream) {

                requestStream.setOnReadyHandler(() -> {
                    Thread.ofVirtual().start(() -> {
                        logger.info(">>>---> iniciando guarda de resposta");
                        try {
                            while (true) {
                                if (!resultado.empty()) {
                                    List<PaymentData> lista = new ArrayList<>();
                                    lista.add(resultado.pop());
                                    PaymentList pl = PaymentList.newBuilder()
                                            .setSize(lista.size())
                                            .addAllItems(lista)
                                            .build();
                                    requestStream.onNext(pl);

                                }
                            }
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Quda da guarda de reposta.");
                            e.printStackTrace();
                        }
                    });
                    logger.info(">>>---> NO AR fluxo requisicoes!");
                });
            }

            @Override
            public void onNext(PaymentList value) {

                PaymentsBackClient.processamento.addAll(value.getItemsList());

            }

            @Override
            public void onCompleted() {
                logger.info("All Done");
                done.countDown();
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                done.countDown();
            }

        };

        // Processaento dos dados que serão enviados pelo servidor.
        ClientResponseObserver<ControlData, ControlData> controlResponseObserver = new ClientResponseObserver<ControlData, ControlData>() {

            @Override
            public void beforeStart(final ClientCallStreamObserver<ControlData> requestStream) {

                requestStream.setOnReadyHandler(() -> {
                    // Nada implementado aqui.
                    logger.info(">>>---> NO AR control estado!");
                });

            }

            @Override
            public void onNext(ControlData value) {
                PaymentsClient.setStatus(value);
            }

            @Override
            public void onCompleted() {
                logger.info("All Done");
                done.countDown();
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                done.countDown();
            }

        };

        Thread.ofVirtual().start(() -> {
            logger.info(">>>---> iniciando guarda de processamento de pagamento.");
            while (true) {
                if (!processamento.empty() && PaymentsClient.isAlmostReady() &&  Thread.activeCount() < 3001) {
                    try {
                        PaymentData pd = processamento.pop();
                        Thread virtualThread = Thread.ofVirtual().start(() -> {
                            PaymentsClient pc = new PaymentsClient();
                            pc.processPayment(pd);
                        });

                        virtualThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        Thread.ofVirtual().start(() -> {
            logger.info(">>>---> iniciando guarda do rebote.");

            while (true) {
                if (!rebote.empty() && PaymentsClient.isAlmostReady()) {

                    PaymentsClient pc = new PaymentsClient();
                    PaymentData pd = rebote.pop();

                    Thread virtualThread = Thread.ofVirtual().start(() -> {
                        pc.processPayment(pd);
                    });
                    // virtualThread.join();

                }
            }
        });

        Thread.ofVirtual().start(() -> {
            logger.info(">>>---> iniciando guarda de monitoracao.");

            while (true) {
                try {
                    logger.info(">>>---> Threads: " + Thread.activeCount());

                    Thread.sleep(Duration.ofSeconds(10));
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });

        stub.streamControls(controlResponseObserver);
        stub.streamPayments(clientResponseObserver);

        done.await();

        channel.shutdown();
        channel.awaitTermination(1, TimeUnit.SECONDS);

    }

}
