package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.CompanyRepository;
import com.example.demo.Repository.JobRepository;
import jakarta.annotation.PostConstruct;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BackupService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final String BACKUP_DIR = "backups";

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final Driver driver;

    public BackupService(JobRepository jobRepository, CompanyRepository companyRepository, Driver driver) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.driver = driver;
    }

    @PostConstruct
    public void init() {
        // Run cleanup on application startup as well
        cleanUpOldBackups();
    }

    @Override
    public void destroy() {
        log.info("Shutdown detectat. Se generează backup-ul final în format Memgraph...");
        try {
            createBackup();
            // IMPORTANT: Lăsăm timp sistemului de operare să închidă fișierul fizic corect
            Thread.sleep(1000);
        } catch (Exception e) {
            log.error("Eroare la scrierea backup-ului în timpul shutdown-ului!", e);
        }
    }

    public String createBackup() throws IOException {
        // Preluăm datele
        List<Company> validCompanies = companyRepository.findAll().stream()
                .filter(c -> c != null && c.getName() != null && !c.getName().isBlank())
                .collect(Collectors.toList());

        List<Job> validJobs = jobRepository.findAll().stream()
                .filter(j -> j != null && j.getAdzunaId() != null && !j.getAdzunaId().isBlank())
                .collect(Collectors.toList());

        if (validCompanies.isEmpty() && validJobs.isEmpty()) return null;

        // Pregătim folderul
        File backupDir = new File(BACKUP_DIR);
        if (!backupDir.exists()) backupDir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String fileName = String.format("backup_memgraph_%s.cypherl", timestamp);
        Path filePath = Paths.get(BACKUP_DIR, fileName);

        // Folosim PrintWriter cu autoflush activat (true) pentru a evita eroarea <EOF>
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath, StandardCharsets.UTF_8), true)) {

            // 1. Creăm Nodurile de tip Company (stil Memgraph)
            for (Company c : validCompanies) {
                // Înlocuim CREATE cu MERGE
                writer.println(String.format("MERGE (:`Company` {`name`: '%s'});", escapeCypher(c.getName())));
            }

            // 2. Creăm Nodurile de tip Job
            for (Job j : validJobs) {
                // Folosim MERGE cu SET
                writer.println(String.format(
                        "MERGE (j:`Job` {`adzunaId`: '%s'}) SET j.`title` = '%s', j.`location` = '%s', j.`country` = '%s', j.`category` = '%s', j.`url` = '%s', j.`description` = '%s';",
                        escapeCypher(j.getAdzunaId()),
                        escapeCypher(j.getTitle()),
                        escapeCypher(j.getLocation()),
                        escapeCypher(j.getCountry()),
                        escapeCypher(j.getCategory()),
                        escapeCypher(j.getUrl()),
                        escapeCypher(j.getDescription())
                ));
            }

            // 3. Creăm Relațiile (folosind MATCH pe proprietățile create anterior)
            for (Job j : validJobs) {
                if (j.getCompany() != null && j.getCompany().getName() != null) {
                    writer.println(String.format(
                            "MATCH (u:`Job`), (v:`Company`) WHERE u.`adzunaId` = '%s' AND v.`name` = '%s' MERGE (u)-[:`POSTED_BY`]->(v);",
                            escapeCypher(j.getAdzunaId()),
                            escapeCypher(j.getCompany().getName())
                    ));
                }
            }

            // Flush final pentru siguranță totală
            writer.flush();
        }

        log.info("Fișier de backup generat în format compatibil: {}", fileName);

        // Clean up old backups after creating a new one
        cleanUpOldBackups();

        return fileName;
    }

    private void cleanUpOldBackups() {
        log.info("Starting cleanup of old backup files...");
        File backupDir = new File(BACKUP_DIR);
        if (!backupDir.exists() || !backupDir.isDirectory()) {
            return;
        }

        Instant threshold = Instant.now().minus(12, ChronoUnit.HOURS);
        int deletedCount = 0;

        try (Stream<Path> paths = Files.walk(Paths.get(BACKUP_DIR), 1)) {
            List<Path> oldBackups = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("backup_") && name.endsWith(".cypherl");
                    })
                    .filter(path -> {
                        try {
                            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                            // It's safer to check last modified time in case creation time is altered
                            return attr.lastModifiedTime().toInstant().isBefore(threshold);
                        } catch (IOException e) {
                            log.warn("Could not read attributes for file {}: {}", path, e.getMessage());
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            for (Path oldBackup : oldBackups) {
                try {
                    Files.delete(oldBackup);
                    log.info("Deleted old backup file: {}", oldBackup.getFileName());
                    deletedCount++;
                } catch (IOException e) {
                    log.error("Failed to delete old backup file {}: {}", oldBackup.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Error walking backup directory during cleanup", e);
        }

        if (deletedCount > 0) {
            log.info("Finished backup cleanup. Deleted {} old files.", deletedCount);
        } else {
            log.info("Finished backup cleanup. No old files found to delete.");
        }
    }

    public void loadBackup(String filename) throws IOException {
        Path filePath = Paths.get(BACKUP_DIR, filename);
        if (!Files.exists(filePath)) {
            throw new IOException("Backup file not found: " + filename);
        }

        log.info("Starting to load backup from CypherL script: {}", filename);
        
        // Înainte de a încărca noul backup, ștergem baza de date completă pentru a preveni coliziunile și nodurile orfane.
        // Aceasta e cea mai sigură și curată metodă când importăm un backup complet.
        log.info("Clearing existing database before loading backup...");
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (n) DETACH DELETE n");
                return null;
            });
            log.info("Database cleared successfully.");
        } catch (Exception e) {
            log.error("Failed to clear database before backup load. Aborting load.", e);
            throw new RuntimeException("Failed to clear database before loading backup.", e);
        }

        int lineCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("//")) {
                    continue; // Skip empty lines and comments
                }
                
                // Execute each line in its own session and transaction for safety
                try (Session session = driver.session()) {
                    session.executeWrite(tx -> {
                        tx.run(trimmedLine);
                        return null;
                    });
                    lineCount++;
                } catch (Exception e) {
                    log.error("Failed to execute line from backup: [{}]", trimmedLine, e);
                    throw new RuntimeException("Failed on line: " + trimmedLine, e);
                }
            }
        }
        log.info("Successfully executed {} commands from backup file {}.", lineCount, filename);
    }

    private String escapeCypher(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public List<String> listBackupFiles() {
        File backupDir = new File(BACKUP_DIR);
        if (!backupDir.exists() || !backupDir.isDirectory()) return Collections.emptyList();
        try (Stream<Path> paths = Files.walk(Paths.get(BACKUP_DIR), 1)) {
            return paths.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("backup_") && name.endsWith(".cypherl"))
                    .sorted(Collections.reverseOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}