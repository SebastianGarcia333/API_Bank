package com.bankapi.dto;

import com.bankapi.domain.LedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        String kind,
        String reference,
        String counterpartyNumber,
        String description,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant createdAt) {

    public static TransactionResponse from(LedgerEntry entry) {
        return new TransactionResponse(
                entry.getId(),
                entry.getKind().name().toLowerCase(),
                entry.getReference(),
                entry.getCounterpartyNumber(),
                entry.getDescription(),
                BigDecimal.valueOf(entry.getAmountCents(), 2),
                BigDecimal.valueOf(entry.getBalanceAfterCents(), 2),
                entry.getCreatedAt());
    }
}
