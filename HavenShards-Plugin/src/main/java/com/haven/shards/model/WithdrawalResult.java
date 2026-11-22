package com.haven.shards.model;

public class WithdrawalResult {
    private final boolean success;
    private final double balance;

    public WithdrawalResult(boolean success, double balance) {
        this.success = success;
        this.balance = balance;
    }

    public boolean isSuccess() {
        return success;
    }

    public double getBalance() {
        return balance;
    }
}
