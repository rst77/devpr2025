package com.r13a.rinha2025.entity;

public class Health {
    private boolean statusA;
    private int minResponseTimeA;
    private boolean statusB;
    private int minResponseTimeB;
    public boolean isStatusA() {
        return statusA;
    }
    public void setStatusA(boolean statusA) {
        this.statusA = statusA;
    }
    public int getMinResponseTimeA() {
        return minResponseTimeA;
    }
    public void setMinResponseTimeA(int minResponseTimeA) {
        this.minResponseTimeA = minResponseTimeA;
    }
    public boolean isStatusB() {
        return statusB;
    }
    public void setStatusB(boolean statusB) {
        this.statusB = statusB;
    }
    public int getMinResponseTimeB() {
        return minResponseTimeB;
    }
    public void setMinResponseTimeB(int minResponseTimeB) {
        this.minResponseTimeB = minResponseTimeB;
    }
    public Health() {
        
    }
    public Health(boolean statusA, int minResponseTimeA, boolean statusB, int minResponseTimeB) {
        this.statusA = statusA;
        this.minResponseTimeA = minResponseTimeA;
        this.statusB = statusB;
        this.minResponseTimeB = minResponseTimeB;
    }
    
}
