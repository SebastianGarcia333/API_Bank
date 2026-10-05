package com.bankapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Libro mayor: cada movimiento deja una fila inmutable. */
@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_account", columnList = "account_id"),
        @Index(name = "idx_ledger_reference", columnList = "reference")
})
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TxKind kind;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(name = "balance_after_cents", nullable = false, updatable = false)
    private long balanceAfterCents;

    /** Las dos patas de una transferencia comparten referencia. */
    @Column(nullable = false, updatable = false, length = 32)
    private String reference;

    @Column(name = "counterparty_number", updatable = false, length = 12)
    private String counterpartyNumber;

    @Column(updatable = false, length = 140)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LedgerEntry() {
    }

    public LedgerEntry(Long accountId, TxKind kind, long amountCents, long balanceAfterCents,
                       String reference, String counterpartyNumber, String description) {
        this.accountId = accountId;
        this.kind = kind;
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
        this.reference = reference;
        this.counterpartyNumber = counterpartyNumber;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public TxKind getKind() {
        return kind;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getBalanceAfterCents() {
        return balanceAfterCents;
    }

    public String getReference() {
        return reference;
    }

    public String getCounterpartyNumber() {
        return counterpartyNumber;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
