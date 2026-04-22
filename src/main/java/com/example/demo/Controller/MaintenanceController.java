package com.example.demo.Controller;

import com.example.demo.Service.DataMaintenanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final DataMaintenanceService dataMaintenanceService;

    public MaintenanceController(DataMaintenanceService dataMaintenanceService) {
        this.dataMaintenanceService = dataMaintenanceService;
    }

    /**
     * Returnează lista tuturor task-urilor de import configurate.
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<String>> getAvailableTasks() {
        return ResponseEntity.ok(dataMaintenanceService.getAvailableImportTasks());
    }

    /**
     * Endpoint pentru a declanșa manual un singur task de import.
     * @param taskName Numele task-ului de executat (ex: "arbeitnow", "adzuna-gb").
     */
    @PostMapping("/trigger-import/{taskName}")
    public ResponseEntity<String> triggerSingleImportTask(@PathVariable String taskName) {
        // Metoda de import este deja asincronă, deci nu mai este nevoie de 'new Thread()'
        dataMaintenanceService.triggerSingleImportTask(taskName);
        return ResponseEntity.ok("Task '" + taskName + "' started in the background.");
    }

    /**
     * Endpoint pentru a declanșa manual curățarea săptămânală a bazei de date.
     */
    @PostMapping("/trigger-pruning")
    public ResponseEntity<String> triggerDatabasePruning() {
        new Thread(() -> dataMaintenanceService.triggerWeeklyPruning()).start();
        return ResponseEntity.ok("Database pruning process started in the background.");
    }

    /**
     * Returnează lista tuturor arhivelor de pruning disponibile.
     */
    @GetMapping("/pruning-archives")
    public ResponseEntity<List<String>> getAvailablePruningArchives() {
        File archiveDir = new File("archives");
        if (!archiveDir.exists() || !archiveDir.isDirectory()) {
            return ResponseEntity.ok(List.of());
        }

        File[] files = archiveDir.listFiles((dir, name) -> name.endsWith(".zip") && name.startsWith("pruned_jobs_"));
        if (files == null) {
            return ResponseEntity.ok(List.of());
        }

        List<String> fileNames = Arrays.stream(files)
                .map(File::getName)
                .collect(Collectors.toList());

        return ResponseEntity.ok(fileNames);
    }

    /**
     * Endpoint pentru a restaura datele dintr-o arhivă de pruning.
     */
    @PostMapping("/restore-archive")
    public ResponseEntity<String> restoreFromArchive(@RequestBody RestoreRequest request) {
        if (request == null || request.getFilename() == null || request.getFilename().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Filename is required.");
        }

        try {
            dataMaintenanceService.restoreFromArchive(request.getFilename());
            return ResponseEntity.ok("Successfully restored data from archive: " + request.getFilename());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to restore from archive: " + e.getMessage());
        }
    }

    public static class RestoreRequest {
        private String filename;

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }
    }
}