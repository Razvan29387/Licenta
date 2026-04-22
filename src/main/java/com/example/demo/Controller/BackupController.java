package com.example.demo.Controller;

import com.example.demo.Service.BackupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backups")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping
    public ResponseEntity<List<String>> listBackups() {
        return ResponseEntity.ok(backupService.listBackupFiles());
    }

    @PostMapping("/load")
    public ResponseEntity<?> loadBackup(@RequestBody Map<String, String> payload) {
        String filename = payload.get("filename");
        if (filename == null || filename.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Filename is required."));
        }

        try {
            backupService.loadBackup(filename);
            return ResponseEntity.ok(Map.of("message", "Successfully loaded backup: " + filename));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to load backup: " + e.getMessage()));
        }
    }
}