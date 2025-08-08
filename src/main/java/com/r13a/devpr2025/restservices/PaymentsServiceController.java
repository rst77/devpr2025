package com.r13a.devpr2025.restservices;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.r13a.devpr2025.entity.Ticket;
import com.r13a.devpr2025.grpc.PaymentData;
import com.r13a.devpr2025.grpc.PaymentDataOrBuilder;
import com.r13a.devpr2025.grpc.PaymentServiceGrpc.PaymentServiceBlockingStub;
import com.r13a.devpr2025.grpcservices.PaymentsFrontServer;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import jakarta.inject.Inject;

@Controller("/")
public class PaymentsServiceController {
  private static final Logger logger = Logger.getLogger(PaymentsServiceController.class.getName());

  /*
   * @Get("/payments")
   * 
   * @Produces(MediaType.APPLICATION_JSON)
   * public GreeterResponse send(
   * 
   * @Nullable
   * 
   * @QueryValue(value = "name")
   * Optional<String> name
   * ){
   * 
   * logger.info(">>---> recebida mensagem: " + name.get());
   * PaymentsFrontServer.list.add(name.get());
   * //GreeterRequest request =
   * GreeterRequest.newBuilder().setName(name.get()).build();
   * 
   * //GreeterReply x = greeterServiceStub.send(request);
   * 
   * return new GreeterResponse("OK");
   * }
   */

  @Post("payments/")
  @Produces(MediaType.TEXT_PLAIN)
  @SuppressWarnings("ConvertToTryWithResources")
  public String payments(@Body Ticket ticket) throws Exception {

    PaymentData pd = PaymentData.newBuilder()
                        .setCorrelationId( ticket.getCorrelationId().toString() )
                        .setAmount( ticket.getAmount() )
                        .setRequestedAt( Instant.now().toString() )
                        .setStatus(1)
                        .setService(0).build();

    PaymentsFrontServer.list.add(pd);

    return "ok";
  }

  @Get("/payments-summary")
  @Produces(MediaType.TEXT_PLAIN)
  public String payments(@QueryValue Instant from, @QueryValue Instant to) throws Exception {

    int totalDefault = 0;
    double somaDefault = 0;
    long totalBackup = 0;
    double somaBackup = 0;

    

    System.out.println("\n>>----->  from: " + from);
    System.out.println(">>----->  to  : " + to);

    List<Entry<Long,PaymentData>> list = PaymentsFrontServer.result
                                      .entrySet()
                                      .stream()
                                      .filter( e -> e.getKey() >= from.toEpochMilli() && e.getKey() <= to.toEpochMilli()).toList();

    for(Entry<Long, PaymentData> d : list) {
      if (d.getValue().getService() == 1) {
        totalDefault++;
        somaDefault += d.getValue().getAmount();
      } else {
        totalBackup++;
        somaBackup += d.getValue().getAmount();
      }

    }

    String retorno = "{" +
        "\"default\" : {" +
        "\"totalRequests\": " + totalDefault + "," +
        "\"totalAmount\": " + somaDefault +
        "}," +
        "\"fallback\" : {" +
        "\"totalRequests\": " + totalBackup + "," +
        "\"totalAmount\": " + somaBackup +
        "}" +
        "}";
    logger.info(retorno);
    return retorno;
  }

}
