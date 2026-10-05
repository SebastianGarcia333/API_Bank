package com.bankapi.web;

import com.bankapi.dto.AccountResponse;
import com.bankapi.dto.AmountRequest;
import com.bankapi.dto.CreateAccountRequest;
import com.bankapi.dto.TransactionResponse;
import com.bankapi.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@AuthenticationPrincipal Long userId,
                                  @Valid @RequestBody(required = false) CreateAccountRequest request) {
        CreateAccountRequest body = request != null ? request : new CreateAccountRequest(null);
        return AccountResponse.from(service.open(userId, body.currencyOrDefault()));
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Long userId) {
        return service.list(userId).stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{number}")
    public AccountResponse get(@AuthenticationPrincipal Long userId, @PathVariable String number) {
        return AccountResponse.from(service.get(userId, number));
    }

    @PostMapping("/{number}/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(@AuthenticationPrincipal Long userId,
                                       @PathVariable String number,
                                       @Valid @RequestBody AmountRequest request) {
        return TransactionResponse.from(
                service.deposit(userId, number, request.amount(), request.description()));
    }

    @PostMapping("/{number}/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse withdraw(@AuthenticationPrincipal Long userId,
                                        @PathVariable String number,
                                        @Valid @RequestBody AmountRequest request) {
        return TransactionResponse.from(
                service.withdraw(userId, number, request.amount(), request.description()));
    }

    @GetMapping("/{number}/transactions")
    public List<TransactionResponse> transactions(@AuthenticationPrincipal Long userId,
                                                  @PathVariable String number,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.history(userId, number, page, size).stream()
                .map(TransactionResponse::from)
                .toList();
    }
}
