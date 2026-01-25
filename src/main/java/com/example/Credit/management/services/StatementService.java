package com.example.Credit.management.services;
import com.example.Credit.management.Model.Statement;
import com.example.Credit.management.Repository.StatementRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatementService {

    private final StatementRepository statementRepository;

    public StatementService(StatementRepository statementRepository) {
        this.statementRepository = statementRepository;
    }

    public List<Statement> saveAll(List<Statement> statements) {
        return statementRepository.saveAll(statements);
    }

    public List<Statement> getByNationalId(String nationalId) {
        return statementRepository.findByNationalIdOrderByDateAsc(nationalId);
    }

    public Statement save(Statement st) {
        return statementRepository.save(st);
    }

}
