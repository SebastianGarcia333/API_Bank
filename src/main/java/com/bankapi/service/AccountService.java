package com.bankapi.service;

import com.bankapi.domain.Account;
import com.bankapi.domain.LedgerEntry;
import com.bankapi.domain.TxKind;
import com.bankapi.dto.TransferRequest;
import com.bankapi.exception.BankException;
import com.bankapi.repository.AccountRepository;
import com.bankapi.repository.LedgerEntryRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public AccountService(AccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional
    public Account open(Long userId, String currency) {
        return accounts.saveAndFlush(new Account(newAccountNumber(), userId, currency));
    }

    @Transactional(readOnly = true)
    public List<Account> list(Long userId) {
        return accounts.findByOwnerIdOrderByIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public Account get(Long userId, String number) {
        return owned(userId, number);
    }

    @Transactional
    public LedgerEntry deposit(Long userId, String number, BigDecimal amount, String description) {
        Account account = owned(userId, number);
        long cents = toCents(amount);
        ensureActive(account);
        long balance = credit(account, cents);
        return ledger.save(new LedgerEntry(
                account.getId(), TxKind.DEPOSIT, cents, balance, newReference(), null, description));
    }

    @Transactional
    public LedgerEntry withdraw(Long userId, String number, BigDecimal amount, String description) {
        Account account = owned(userId, number);
        long cents = toCents(amount);
        ensureActive(account);
        long balance = debit(account, cents);
        return ledger.save(new LedgerEntry(
                account.getId(), TxKind.WITHDRAWAL, cents, balance, newReference(), null, description));
    }

    @Transactional
    public LedgerEntry transfer(Long userId, TransferRequest request) {
        Account source = owned(userId, request.sourceNumber());
        if (source.getNumber().equals(request.destinationNumber())) {
            throw BankException.unprocessable("La cuenta de origen y destino no pueden ser la misma");
        }
        Account destination = accounts.findByNumber(request.destinationNumber())
                .orElseThrow(() -> BankException.notFound("Cuenta de destino no encontrada"));
        ensureActive(source);
        ensureActive(destination);
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw BankException.unprocessable("Las cuentas deben tener la misma moneda");
        }

        long cents = toCents(request.amount());
        long sourceBalance;
        long destinationBalance;
        // Se actualiza siempre primero la cuenta con menor id para evitar bloqueos cruzados
        // entre transferencias simultáneas A->B y B->A.
        if (source.getId() < destination.getId()) {
            sourceBalance = debit(source, cents);
            destinationBalance = credit(destination, cents);
        } else {
            destinationBalance = credit(destination, cents);
            sourceBalance = debit(source, cents);
        }

        String reference = newReference();
        LedgerEntry out = ledger.save(new LedgerEntry(
                source.getId(), TxKind.TRANSFER_OUT, cents, sourceBalance,
                reference, destination.getNumber(), request.description()));
        ledger.save(new LedgerEntry(
                destination.getId(), TxKind.TRANSFER_IN, cents, destinationBalance,
                reference, source.getNumber(), request.description()));
        return out;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> history(Long userId, String number, int page, int size) {
        Account account = owned(userId, number);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ledger.findByAccountIdOrderByIdDesc(account.getId(), PageRequest.of(safePage, safeSize));
    }

    // -- helpers -----------------------------------------------------------------------------

    /** Devuelve 404 también si la cuenta es de otro usuario, para no revelar que existe. */
    private Account owned(Long userId, String number) {
        return accounts.findByNumberAndOwnerId(number, userId)
                .orElseThrow(() -> BankException.notFound("Cuenta no encontrada"));
    }

    private void ensureActive(Account account) {
        if (!account.isActive()) {
            throw BankException.conflict("La cuenta está inactiva");
        }
    }

    private long debit(Account account, long cents) {
        if (accounts.applyDelta(account.getId(), -cents) != 1) {
            throw BankException.conflict("Fondos insuficientes");
        }
        return accounts.balanceOf(account.getId());
    }

    private long credit(Account account, long cents) {
        if (accounts.applyDelta(account.getId(), cents) != 1) {
            throw BankException.conflict("La cuenta no puede recibir fondos");
        }
        return accounts.balanceOf(account.getId());
    }

    private String newAccountNumber() {
        String number;
        do {
            number = String.format("%012d", RANDOM.nextLong(1_000_000_000_000L));
        } while (accounts.existsByNumber(number));
        return number;
    }

    private static String newReference() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }
}
