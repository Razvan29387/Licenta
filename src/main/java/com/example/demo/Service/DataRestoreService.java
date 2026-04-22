package com.example.demo.Service;

import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipInputStream;

@Service
public class DataRestoreService {

    private static final Logger log = LoggerFactory.getLogger(DataRestoreService.class);

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public DataRestoreService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Transactional
    public void restoreJobsFromArchive(String archiveFileName) throws IOException {
        File archiveDir = new File("archives");
        File archiveFile = new File(archiveDir, archiveFileName);

        if (!archiveFile.exists()) {
            log.error("Archive file not found: {}", archiveFile.getAbsolutePath());
            throw new IOException("Archive file not found: " + archiveFileName);
        }

        log.info("Restoring jobs from: {}", archiveFile.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(archiveFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            zis.getNextEntry(); // Go to the first entry in the zip file

            List<Job> jobsToRestore = objectMapper.readValue(zis, new TypeReference<List<Job>>() {});

            if (jobsToRestore != null && !jobsToRestore.isEmpty()) {
                log.info("Found {} jobs to restore. Saving to database...", jobsToRestore.size());
                jobRepository.saveAll(jobsToRestore);
                log.info("Successfully restored {} jobs.", jobsToRestore.size());
            } else {
                log.warn("No jobs found in the archive.");
            }
        }
    }
}