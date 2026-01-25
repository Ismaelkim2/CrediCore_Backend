package com.example.Credit.management.controller;
import com.example.Credit.management.Model.LoanSettings;
import com.example.Credit.management.services.LoanSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*") // Optional: for frontend access
public class LoanSettingsController {

    @Autowired
    private LoanSettingsService service;

    @GetMapping("/all")
    public ResponseEntity<List<LoanSettings>> getAllSettings() {
        List<LoanSettings> settings = service.getAll();
        return ResponseEntity.ok(settings);
    }

    @PostMapping
    public ResponseEntity<LoanSettings> save(@RequestBody LoanSettings setting) {
        return ResponseEntity.ok(service.save(setting));
    }

    @GetMapping("/duration/{weeks}")
    public ResponseEntity<LoanSettings> getByDuration(@PathVariable int weeks) {
        return service.getByDuration(weeks)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<LoanSettings> getSystemSettings() {
        return ResponseEntity.ok(service.getSettings());
    }

    @PutMapping
    public ResponseEntity<LoanSettings> update(@RequestBody LoanSettings settings) {
        return ResponseEntity.ok(service.updateSettings(settings));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSetting(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
