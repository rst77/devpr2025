package com.r13a.devpr2025.entity;

public class Total {       
    public int totalDefault = 0;
    public double somaDefault = 0;
    public int totalFallback = 0;
    public double somaFallback = 0;

    public Total() {
        
    }
    public int getTotalDefault() {
        return totalDefault;
    }

    public void setTotalDefault(int totalDefault) {
        this.totalDefault = totalDefault;
    }

    public double getSomaDefault() {
        return somaDefault;
    }

    public void setSomaDefault(double somaDefault) {
        this.somaDefault = somaDefault;
    }

    public int getTotalFallback() {
        return totalFallback;
    }

    public void setTotalFallback(int totalFallback) {
        this.totalFallback = totalFallback;
    }

    public double getSomaFallback() {
        return somaFallback;
    }

    public void setSomaFallback(double somaFallback) {
        this.somaFallback = somaFallback;
    }
    
}