package com.r13a.devpr2025.grpcservices;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.r13a.devpr2025.client.HealthClient;
import com.r13a.devpr2025.client.Semaforo;
import com.r13a.devpr2025.grpc.ControlData;
import com.r13a.devpr2025.grpc.PaymentData;
import com.r13a.devpr2025.grpc.PaymentList;
import com.r13a.devpr2025.grpc.PaymentServiceGrpc.PaymentServiceImplBase;
import com.r13a.devpr2025.lista.BackedListListener;
import com.r13a.devpr2025.lista.BackedObservableList;
import com.r13a.devpr2025.lista.ListChangeEvent;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

public class PaymentsFrontServer extends PaymentServiceImplBase {

    Semaforo semaforoEnvio = new Semaforo();
    Semaforo semaforoControle = new Semaforo();

    private static final Logger logger = Logger.getLogger(PaymentsFrontServer.class.getName());

    public static final BackedObservableList<PaymentData> list = new BackedObservableList<>(
            BackedObservableList.Notificacao.SEQUENCIAL);
    public static final Map<Long, PaymentData> result = new HashMap<>();

    public static final BackedObservableList<ControlData> cdList = new BackedObservableList<>(
            BackedObservableList.Notificacao.TODOS);
    private ControlData cd;

    ServerCallStreamObserver<PaymentData> serverCallStreamObserver = null;
    ServerCallStreamObserver<ControlData> serverCallStreamObserverCD = null;

    @Override
    public StreamObserver<ControlData> streamControls(StreamObserver<ControlData> responseObserver) {

        serverCallStreamObserverCD = (ServerCallStreamObserver<ControlData>) responseObserver;

        // Controle que garante a saude das chamadas.
        class OnReadyHandler implements Runnable {
            private boolean wasReady = false;

            @Override
            public void run() {
                if (serverCallStreamObserverCD.isReady() && !wasReady) {
                    wasReady = true;
                }
            }
        }

        final OnReadyHandler onReadyHandler = new OnReadyHandler();
        serverCallStreamObserverCD.setOnReadyHandler(onReadyHandler);

        // Subscreve a lista em memória para receber notificacoes de inclusoes
        BackedListListener<ControlData> bList = new BackedListListener<ControlData>() {

            @Override
            public void setOnChanged(ListChangeEvent<ControlData> event) {
                if (event.wasAdded()) {
                    event.getChangeList().forEach(e -> {
                        while (!semaforoControle.getLock()) {
                        }
                        responseObserver.onNext(e);
                        semaforoControle.releaseLock();
                    });
                }
                if (event.wasRemoved()) {

                    // do whatever you need to dl
                    event.getChangeList().forEach(e -> {
                        System.out.println(e + " was removed");
                    });
                }
            }

        };
        cdList.addListener(bList);

        // Retorna o stream de requisicao para poder receber nas notificacoes de
        // completude de atividade
        StreamObserver<ControlData> retorno = new StreamObserver<ControlData>() {

            @Override
            public void onNext(ControlData value) {
                // TODO Auto-generated method stub
                throw new UnsupportedOperationException("Unimplemented method 'onNext'");
            }

            @Override
            public void onError(Throwable t) {
                // Remove a subscricao por saida do grid
                cdList.removeListener(bList);
                t.printStackTrace();
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                // Remove a subscricao por saida do grid
                cdList.removeListener(bList);
                responseObserver.onCompleted();
            }

        };

        return retorno;
    }

    @Override
    public StreamObserver<PaymentList> streamPayments(StreamObserver<PaymentList> responseObserver) {

        BackedListListener<PaymentData> bList = new BackedListListener<PaymentData>() {

            @Override
            public void setOnChanged(ListChangeEvent<PaymentData> event) {
                if (event.wasAdded()) {
                    while (!semaforoEnvio.getLock()) {
                    }
                    // System.out.println(" enviando " + event.getChangeList().size());
                    PaymentList pl = PaymentList.newBuilder()
                            .setSize(event.getChangeList().size())
                            .addAllItems(event.getChangeList())
                            .build();
                    responseObserver.onNext(pl);
                    semaforoEnvio.releaseLock();
                }
            }

        };
        list.addListener(bList);

        // Retorna o stream de requisicao para poder receber nas notificacoes de
        // completude de atividade
        StreamObserver<PaymentList> retorno = new StreamObserver<PaymentList>() {

            @Override
            public void onNext(PaymentList request) {
                for (PaymentData o : request.getItemsList()) {
                    PaymentsFrontServer.result.put(Instant.parse(o.getRequestedAt()).toEpochMilli(), o);
                }
            }

            @Override
            public void onError(Throwable t) {
                // Remove a subscricao por saida do grid
                list.removeListener(bList);
                t.printStackTrace();
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                // Remove a subscricao por saida do grid
                list.removeListener(bList);
                responseObserver.onCompleted();
            }
        };

        return retorno;
    }

}
