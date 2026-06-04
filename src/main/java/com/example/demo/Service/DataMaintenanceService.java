package com.example.demo.Service;

import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class DataMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(DataMaintenanceService.class);
    private static final int BATCH_SIZE = 500;

    private static final int ADZUNA_DAILY_REQUEST_BUDGET = 240;
    private static final int ARBEITNOW_PAGES_TO_FETCH = 30;
    private static final List<String> ADZUNA_TARGET_COUNTRIES = Arrays.asList(
            "gb", "us", "de", "fr", "ca", "au", "nl", "in", "es", "it", "br", "pl"
    );

    private final AdzunaIngestionService adzunaIngestionService;
    private final ArbeitnowIngestionService arbeitnowIngestionService;
    private final JsearchIngestionService jsearchIngestionService;
    private final RemotiveIngestionService remotiveIngestionService;
    private final HimalayasIngestionService himalayasIngestionService;
    private final JoobleIngestionService joobleIngestionService;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final BatchJobSaverService batchJobSaverService;

    public DataMaintenanceService(AdzunaIngestionService adzunaIngestionService,
                                  ArbeitnowIngestionService arbeitnowIngestionService,
                                  JsearchIngestionService jsearchIngestionService,
                                  RemotiveIngestionService remotiveIngestionService,
                                  HimalayasIngestionService himalayasIngestionService,
                                  JoobleIngestionService joobleIngestionService,
                                  JobRepository jobRepository,
                                  BatchJobSaverService batchJobSaverService) {
        this.adzunaIngestionService = adzunaIngestionService;
        this.arbeitnowIngestionService = arbeitnowIngestionService;
        this.jsearchIngestionService = jsearchIngestionService;
        this.remotiveIngestionService = remotiveIngestionService;
        this.himalayasIngestionService = himalayasIngestionService;
        this.joobleIngestionService = joobleIngestionService;
        this.jobRepository = jobRepository;
        this.batchJobSaverService = batchJobSaverService;
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }

    public List<String> getAvailableImportTasks() {
        List<String> tasks = new ArrayList<>();
        tasks.add("arbeitnow");
        tasks.add("jsearch-it");
        tasks.add("remotive-software");
        tasks.add("himalayas-remote");
        tasks.add("jooble-it");
        for (String country : ADZUNA_TARGET_COUNTRIES) {
            tasks.add("adzuna-" + country);
        }
        return tasks;
    }

    @Transactional
    public void triggerSingleImportTask(String taskName) {
        log.info("Executing single task: {}", taskName);
        if ("arbeitnow".equalsIgnoreCase(taskName)) {
            arbeitnowIngestionService.importJobs(1, ARBEITNOW_PAGES_TO_FETCH);
        } else if ("jsearch-it".equalsIgnoreCase(taskName)) {
            jsearchIngestionService.importJobs("IT software developer engineer", 20);
        } else if ("remotive-software".equalsIgnoreCase(taskName)) {
            remotiveIngestionService.importJobs();
        } else if ("himalayas-remote".equalsIgnoreCase(taskName)) {
            himalayasIngestionService.importJobs(100, 0);
        } else if ("jooble-it".equalsIgnoreCase(taskName)) {
            joobleIngestionService.importJobs("IT", null, 5);
        } else if (taskName.toLowerCase().startsWith("adzuna-")) {
            String countryCode = taskName.substring(7);
            int pagesPerCountry = ADZUNA_DAILY_REQUEST_BUDGET / ADZUNA_TARGET_COUNTRIES.size();
            if (pagesPerCountry < 1) pagesPerCountry = 1;
            adzunaIngestionService.importJobs(countryCode, 1, pagesPerCountry);
        } else {
            log.warn("Unknown task name: {}", taskName);
        }
    }

    public Map<String, Integer> populateByKeywords(String keywords) {
        log.info("Starting keyword-based population for: '{}'", keywords);
        // For the demo, we fetch a small number of pages to get a quick result.
        return jsearchIngestionService.importJobsSync(keywords, 2);
    }

    @Scheduled(cron = "0 0 15 * * ?")
    public void triggerDailyFullDataImport() {
        log.info("--- STARTING SCHEDULED DAILY FULL DATA INGESTION ---");
        List<String> allTasks = getAvailableImportTasks();
        log.info("Found {} tasks to execute in parallel: {}", allTasks.size(), allTasks);
        for (String taskName : allTasks) {
            triggerSingleImportTask(taskName);
        }
        log.info("--- ALL IMPORT TASKS HAVE BEEN LAUNCHED ---");
    }

    @Scheduled(cron = "0 0 5 * * SUN")
    @Transactional
    public void triggerWeeklyPruning() {
        log.info("--- STARTING WEEKLY DATABASE PRUNING ---");

        LocalDateTime timeThreshold = LocalDateTime.now().minusDays(21);
        log.info("Pruning jobs older than 21 days (threshold: {}).", timeThreshold);

        List<Job> oldJobs = jobRepository.findByCreatedAtBefore(timeThreshold);

        if (oldJobs.isEmpty()) {
            log.info("No jobs older than 21 days found to prune.");
            return;
        }

        log.info("Found {} jobs older than 21 days to be pruned.", oldJobs.size());
        try {
            archiveJobs(oldJobs);
            log.info("Deleting {} old jobs from the database...", oldJobs.size());
            jobRepository.deleteAll(oldJobs);
            log.info("Successfully deleted old jobs.");
        } catch (IOException e) {
            log.error("CRITICAL: Failed to archive or delete old jobs.", e);
        }
        log.info("--- WEEKLY DATABASE PRUNING FINISHED ---");
    }

    private void archiveJobs(List<Job> jobs) throws IOException {
        File archiveDir = new File("archives");
        if (!archiveDir.exists()) archiveDir.mkdirs();
        String archiveFileName = "pruned_jobs_" + System.currentTimeMillis() + ".zip";
        File archiveFile = new File(archiveDir, archiveFileName);
        log.info("Archiving jobs to: {}", archiveFile.getAbsolutePath());
        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            ZipEntry jsonEntry = new ZipEntry("jobs.json");
            zos.putNextEntry(jsonEntry);
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(zos, jobs);
            
            zos.closeEntry();
        }
        log.info("Archiving completed successfully.");
    }

    public void restoreFromArchive(String archiveFileName) throws IOException {
        File archiveDir = new File("archives");
        File archiveFile = new File(archiveDir, archiveFileName);

        if (!archiveFile.exists()) {
            throw new IOException("Archive file not found: " + archiveFileName);
        }

        log.info("Starting restoration from archive: {}", archiveFile.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(archiveFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry jsonEntry = zis.getNextEntry();
            if (jsonEntry != null && jsonEntry.getName().equals("jobs.json")) {
                List<Job> restoredJobs = objectMapper.readValue(zis, new TypeReference<List<Job>>() {});

                if (restoredJobs != null && !restoredJobs.isEmpty()) {
                    log.info("Found {} jobs in the archive. Checking for duplicates before saving...", restoredJobs.size());
                    
                    List<Job> jobsToSave = new ArrayList<>();
                    for (Job job : restoredJobs) {
                        if (job.getAdzunaId() != null && !jobRepository.existsByAdzunaId(job.getAdzunaId())) {
                            jobsToSave.add(job);
                        }
                    }

                    if (!jobsToSave.isEmpty()) {
                        log.info("Attempting to save {} new jobs in batches of {}.", jobsToSave.size(), BATCH_SIZE);
                        
                        for (int i = 0; i < jobsToSave.size(); i += BATCH_SIZE) {
                            int end = Math.min(i + BATCH_SIZE, jobsToSave.size());
                            List<Job> batch = jobsToSave.subList(i, end);
                            log.info("Processing batch {}/{} (jobs {} to {})", (i / BATCH_SIZE) + 1, (jobsToSave.size() / BATCH_SIZE) + 1, i, end - 1);
                            batchJobSaverService.saveJobsInBatch(batch);
                        }

                        log.info("Successfully restored {} jobs from the archive (skipped {} duplicates).", 
                                 jobsToSave.size(), restoredJobs.size() - jobsToSave.size());
                    } else {
                        log.info("All {} jobs from the archive already exist in the database. Nothing to restore.", restoredJobs.size());
                    }

                } else {
                    log.warn("No jobs found inside the archive's jobs.json.");
                }
            } else {
                throw new IOException("Invalid archive format: 'jobs.json' not found.");
            }
        }
    }
}