package com.r13a.devpr2025.entity;

import io.micronaut.serde.annotation.Serdeable;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Serdeable
public class Ticket implements Serializable {

    public enum Status {
        OPEN,
        LOCKED,
        PENDING,
        COMPLETED
    }

    UUID correlationId;
    double amount;
    Instant timestamp;
    Status status;
    
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

 
    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public Ticket (String correlationId, double amount) {
        this.correlationId = UUID.fromString( correlationId );
        this.amount = amount;
    }
}
