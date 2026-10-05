package com.bankapi.repository;

import com.bankapi.domain.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByNumber(String number);

    Optional<Account> findByNumberAndOwnerId(String number, Long ownerId);

    boolean existsByNumber(String number);

    List<Account> findByOwnerIdOrderByIdAsc(Long ownerId);

    /**
     * Suma (o resta, si delta es negativo) al saldo en una sola sentencia.
     * La condición {@code saldo + delta >= 0} vive dentro del UPDATE, así que dos
     * retiros simultáneos no pueden dejar la cuenta en negativo.
     *
     * @return 1 si se aplicó; 0 si la cuenta está inactiva o no hay fondos
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Account a set a.balanceCents = a.balanceCents + :delta "
            + "where a.id = :id and a.active = true and a.balanceCents + :delta >= 0")
    int applyDelta(@Param("id") Long id, @Param("delta") long delta);

    @Query("select a.balanceCents from Account a where a.id = :id")
    long balanceOf(@Param("id") Long id);
}
