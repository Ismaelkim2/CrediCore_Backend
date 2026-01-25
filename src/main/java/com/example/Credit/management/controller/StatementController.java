package com.example.Credit.management.controller;
import com.example.Credit.management.Model.Statement;
import com.example.Credit.management.services.StatementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/statements")
@CrossOrigin(origins = "*")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @PostMapping("/{nationalId}")
    public ResponseEntity<?> saveStatement(@PathVariable String nationalId,
                                           @RequestBody List<Statement> statements) {
        statements.forEach(s -> s.setNationalId(nationalId));
        return ResponseEntity.ok(statementService.saveAll(statements));
    }

    @GetMapping("/{nationalId}")
    public ResponseEntity<List<Statement>> getStatements(@PathVariable String nationalId) {
        return ResponseEntity.ok(statementService.getByNationalId(nationalId));
    }
}
