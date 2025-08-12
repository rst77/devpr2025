package com.r13a.devpr2025.client;


public class Semaforo {

    private boolean locked = false;

    public synchronized boolean getLock() {

        if (!locked) {
            locked = true;
            return true;
        }

        return false;

    }

    public synchronized void releaseLock() {

        locked = false;

    }

}