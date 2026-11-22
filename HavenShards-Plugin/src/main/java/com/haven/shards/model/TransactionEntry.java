package com.haven.shards.model;

public class TransactionEntry {

    private final int id;
    private final String timestamp;
    private final String senderName;
    private final String receiverName;
    private final String type;
    private final double amount;
    private final double taxAmount;
    private final double senderBalAfter;
    private final double receiverBalAfter;

    public TransactionEntry(int id, String timestamp, String senderName, String receiverName,
                            String type, double amount, double taxAmount, double senderBalAfter, double receiverBalAfter) {
        this.id = id;
        this.timestamp = timestamp;
        this.senderName = senderName;
        this.receiverName = receiverName;
        this.type = type;
        this.amount = amount;
        this.taxAmount = taxAmount;
        this.senderBalAfter = senderBalAfter;
        this.receiverBalAfter = receiverBalAfter;
    }

    public TransactionEntry(int id, String timestamp, String senderName, String receiverName,
                            String type, double amount, double senderBalAfter, double receiverBalAfter) {
        this(id, timestamp, senderName, receiverName, type, amount, 0.0, senderBalAfter, receiverBalAfter);
    }

    public int getId() { return id; }
    public String getTimestamp() { return timestamp; }
    public String getSenderName() { return senderName; }
    public String getReceiverName() { return receiverName; }
    public String getType() { return type; }
    public double getAmount() { return amount; }
    public double getTaxAmount() { return taxAmount; }
    public double getSenderBalAfter() { return senderBalAfter; }
    public double getReceiverBalAfter() { return receiverBalAfter; }
}
