package com.alejodev.ledger.model;


import com.alejodev.ledger.model.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Table(name = "transactions")
@ToString
public class Transaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, precision = 19, scale = 4)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(nullable = false)
    private String type; //Puede ser Transfer, Deposit o Withdrawal

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private Instant createdAt;

    private Instant completedAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = Instant.now();
        if(this.status == null ){
            this.status = TransactionStatus.PENDING;
        }
    }

    protected Transaction(){}

    public Transaction(String idempotencyKey, String type, BigDecimal amount, String description){
        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.status = TransactionStatus.PENDING;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
        if (status == TransactionStatus.COMPLETED) {
            this.completedAt = Instant.now();
        }
    }





}
