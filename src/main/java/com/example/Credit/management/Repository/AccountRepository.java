package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByNationalId(String nationalId);
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<Account> findByPhoneNumber(String phoneNumber);
    List<Account> findByNationalIdContaining(String nationalId);

}
