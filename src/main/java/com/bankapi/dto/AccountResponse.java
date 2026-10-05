package com.bankapi.dto;

import com.bankapi.domain.Account;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(String number, String currency, boolean active, BigDecimal balance, Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getNumber(),
                account.getCurrency(),
                account.isActive(),
                BigDecimal.valueOf(account.getBalanceCents(), 2),
                account.getCreatedAt());
    }
}
