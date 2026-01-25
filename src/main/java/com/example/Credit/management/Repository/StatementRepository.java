package com.example.Credit.management.Repository;


import com.example.Credit.management.Model.Statement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatementRepository extends JpaRepository<Statement, Long> {
    List<Statement> findByNationalIdOrderByDateAsc(String nationalId);
}