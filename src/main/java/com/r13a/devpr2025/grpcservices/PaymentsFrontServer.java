package com.r13a.devpr2025.grpcservices;

import java.security.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.r13a.devpr2025.grpc.ControlData;
import com.r13a.devpr2025.grpc.PaymentData;
import com.r13a.devpr2025.grpc.PaymentServiceGrpc.PaymentServiceImplBase;
import com.r13a.devpr2025.lista.BackedListListener;
import com.r13a.devpr2025.lista.BackedObservableList;
import com.r13a.devpr2025.lista.ListChangeEvent;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Singleton;


@Singleton
public class PaymentsFrontServer extends PaymentServiceImplBase {

    private static final Logger logger = Logger.getLogger(PaymentsFrontServer.class.getName());

    public static final BackedObservableList<PaymentData> list = new BackedObservableList<PaymentData>();
    public static final Map<Long,PaymentData> result = new HashMap<Long,PaymentData>();
    

    ServerCallStreamObserver<PaymentData> serverCallStreamObserver = null;

    @Override
    public StreamObserver<ControlData> streamControls(StreamObserver<ControlData> responseObserver) {
        // TODO Auto-generated method stub
        return super.streamControls(responseObserver);
    }


    @Override
    public StreamObserver<PaymentData> streamPayments(StreamObserver<PaymentData> responseObserver) {

        serverCallStreamObserver = (ServerCallStreamObserver<PaymentData>) responseObserver;

        //serverCallStreamObserver.disableAutoRequest();

        // Controle que garante a saude das chamadas.
        class OnReadyHandler implements Runnable {
            private boolean wasReady = false;

            @Override
            public void run() {
                if (serverCallStreamObserver.isReady() && !wasReady) {
                    wasReady = true;
                    //serverCallStreamObserver.request(1);
                }
            }
        }

        final OnReadyHandler onReadyHandler = new OnReadyHandler();
        serverCallStreamObserver.setOnReadyHandler(onReadyHandler);

        // Subscreve a lista em memória para receber notificacoes de inclusoes
        BackedListListener<PaymentData> bList = new BackedListListener<PaymentData>() {

            @Override
            public void setOnChanged(ListChangeEvent<PaymentData> event) {
                if (event.wasAdded()) {
                    event.getChangeList().forEach(e -> {

                        //logger.info(">>---> notificada adicao =" + e.getCorrelationId());
                        System.out.print(".");
                        while (!Semaforo.getLock()) {}

                        responseObserver.onNext(e);
                        Semaforo.releaseLock();

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
        list.addListener(bList);

        // Retorna o stream de requisicao para poder receber nas notificacoes de completude de atividade
        StreamObserver<PaymentData> retorno = new StreamObserver<PaymentData>() {

            @Override
            public void onNext(PaymentData request) {
                //logger.info(">>---> resultado =" + request.getCorrelationId());
                System.out.print("'");
                PaymentsFrontServer.result.put(Instant.parse(request.getRequestedAt()).toEpochMilli(), request);
                //serverCallStreamObserver.request(1);
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

class Semaforo {

    private static boolean locked = false;

    public synchronized static boolean getLock() {

        if (!locked) {
            locked = true;
            return true;
        }

        return false;

    }

    public synchronized static void releaseLock() {

        locked = false;

    }

}