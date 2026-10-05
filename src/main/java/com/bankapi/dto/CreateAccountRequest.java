package com.bankapi.dto;

import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(
        @Pattern(regexp = "^[A-Z]{3}$", message = "debe ser un código ISO de 3 letras mayúsculas") String currency) {

    public String currencyOrDefault() {
        return currency == null ? "USD" : currency;
    }
}
