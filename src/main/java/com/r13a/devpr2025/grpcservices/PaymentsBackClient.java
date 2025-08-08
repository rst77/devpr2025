package com.r13a.devpr2025.grpcservices;

import java.net.http.HttpResponse;
import java.util.EmptyStackException;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.r13a.devpr2025.client.PaymentsClient;
import com.r13a.devpr2025.grpc.PaymentData;
import com.r13a.devpr2025.grpc.PaymentServiceGrpc;
import com.r13a.devpr2025.grpc.PaymentServiceGrpc.PaymentServiceStub;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.netty.util.internal.ThreadExecutorMap;

public class PaymentsBackClient {

    public final static Stack<PaymentData> processamento = new Stack<>();
    public final static Stack<PaymentData> resultado = new Stack<>();
    public final static Stack<PaymentData> rebote = new Stack<>();

    private static final Logger logger = Logger.getLogger(PaymentsBackClient.class.getName());

    public static void main(String[] args) throws InterruptedException {
        logger.info(">>>---> Iniciando cliente");

        final CountDownLatch done = new CountDownLatch(2);

        // Dados para conectar ao serivdor.
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 5000)
                .usePlaintext()
                .build();

        PaymentServiceStub stub = PaymentServiceGrpc.newStub(channel);

        // Processaento dos dados que serão enviados pelo servidor.
        ClientResponseObserver<PaymentData, PaymentData> clientResponseObserver = new ClientResponseObserver<PaymentData, PaymentData>() {

            ClientCallStreamObserver<PaymentData> requestStream;

            @Override
            public void beforeStart(final ClientCallStreamObserver<PaymentData> requestStream) {
                this.requestStream = requestStream;

                //requestStream.disableAutoRequestWithInitial(1);

                requestStream.setOnReadyHandler(new Runnable() {
                    @Override
                    public void run() {
                        Thread.ofVirtual().start(() -> {
                            //logger.info(">>>---> iniciando guarda de resposta");
                            while (true) {
                                if (!resultado.empty()) {
                                    //logger.info(">>>---> Informando resultado");

                                    requestStream.onNext(resultado.pop());
                                }
                            }
                        });                        
                        logger.info(">>>---> NO AR!");
                    }
                });

            }

            @Override
            public void onNext(PaymentData value) {

                PaymentsBackClient.processamento.add(value);
                //requestStream.request(1);

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
            logger.info(">>>---> iniciando guarda de processamento");
            
            while (true) {
                if (!processamento.empty()) {
                        PaymentData pd = processamento.pop();
                    Thread virtualThread = Thread.ofVirtual().start(() -> {
                        PaymentsClient pc = new PaymentsClient();
                        pc.processPayment(pd);
                    });
                    //System.out.print("[" + Thread.activeCount() + "] ");
                    ;
                    //while (Thread.activeCount() > 1500) {}
                }
            }
        });

        Thread.ofVirtual().start(() -> {
            logger.info(">>>---> iniciando guarda de health dos servicos");
            
            while (true) {
                PaymentsClient pc = new PaymentsClient();
                pc.processHealth();
                try {
					Thread.sleep(java.time.Duration.ofSeconds(5));
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        });

        stub.streamPayments(clientResponseObserver);

        done.await();

        channel.shutdown();
        channel.awaitTermination(1, TimeUnit.SECONDS);

    }

}
