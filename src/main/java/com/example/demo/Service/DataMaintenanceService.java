package com.example.demo.Service;

import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class DataMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(DataMaintenanceService.class);
    private static final int BATCH_SIZE = 500;
    private static final int PRUNING_BATCH_SIZE = 50;

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
        this.objectMapper.getFactory().disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
    }

    public List<String> getAvailableImportTasks() {
        List<String> tasks = new ArrayList<>();
        tasks.add("jsearch-it");
        tasks.add("remotive-software");
        tasks.add("arbeitnow");
        tasks.add("himalayas-remote");
        tasks.add("jooble-it");
        for (String country : ADZUNA_TARGET_COUNTRIES) {
            tasks.add("adzuna-" + country);
        }
        return tasks;
    }

    @Transactional("transactionManager")
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

    public void runDemoPopulation(String apiSource, String keywords) {
        log.info("Starting DEMO population for source: '{}', keywords: '{}'", apiSource, keywords);
        
        if (apiSource.toLowerCase().startsWith("adzuna-")) {
            String countryCode = apiSource.substring(7);
            adzunaIngestionService.importJobsAndReportProgress(countryCode, keywords, 1);
        } else if ("jsearch-it".equalsIgnoreCase(apiSource)) {
            jsearchIngestionService.importJobsAndReportProgress(keywords, 2);
        } else if ("remotive-software".equalsIgnoreCase(apiSource)) {
            remotiveIngestionService.importJobsAndReportProgress(keywords);
        } else if ("arbeitnow".equalsIgnoreCase(apiSource)) {
            arbeitnowIngestionService.importJobsAndReportProgress(1);
        } else if ("himalayas-remote".equalsIgnoreCase(apiSource)) {
            himalayasIngestionService.importJobsAndReportProgress(20);
        } else if ("jooble-it".equalsIgnoreCase(apiSource)) {
            joobleIngestionService.importJobsAndReportProgress(keywords, 1);
        } else {
            log.warn("Unsupported API source for demo: {}", apiSource);
        }
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

    @Scheduled(cron = "0 0 15 * * SUN")
    @Transactional("transactionManager")
    public void triggerWeeklyPruning() {
        log.info("--- STARTING BATCHED WEEKLY DATABASE PRUNING (ID-BASED) ---");

        LocalDateTime timeThreshold = LocalDateTime.now().minusDays(14);
        log.info("Finding IDs for jobs older than 14 days (threshold: {}).", timeThreshold);

        List<Long> oldJobIds = jobRepository.findIdsByCreatedAtBefore(timeThreshold);

        if (oldJobIds == null || oldJobIds.isEmpty()) {
            log.info("No old jobs found to prune.");
            return;
        }

        log.info("Found {} job IDs to be pruned. Starting batch processing...", oldJobIds.size());

        File archiveDir = new File("archives");
        if (!archiveDir.exists()) archiveDir.mkdirs();
        String archiveFileName = "pruned_jobs_" + System.currentTimeMillis() + ".zip";
        File archiveFile = new File(archiveDir, archiveFileName);

        int totalArchived = 0;
        int totalDeleted = 0;

        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (int i = 0; i < oldJobIds.size(); i += PRUNING_BATCH_SIZE) {
                int end = Math.min(i + PRUNING_BATCH_SIZE, oldJobIds.size());
                List<Long> batchIds = oldJobIds.subList(i, end);
                
                log.info("Processing pruning batch {} to {} out of {}", i, end, oldJobIds.size());
                
                try {
                    List<Job> jobsBatch = (List<Job>) jobRepository.findAllById(batchIds);
                    
                    if (!jobsBatch.isEmpty()) {
                        archiveJobsBatch(jobsBatch, zos);
                        totalArchived += jobsBatch.size();

                        jobRepository.deleteAll(jobsBatch);
                        totalDeleted += jobsBatch.size();
                    }
                    Thread.sleep(100); 
                } catch (Exception e) {
                    log.error("Error processing batch {} to {}. Skipping this batch. Error: {}", i, end, e.getMessage());
                }
            }

            log.info("Successfully archived {} and deleted {} old jobs.", totalArchived, totalDeleted);

        } catch (Exception e) {
            log.error("CRITICAL: Failed during batch pruning and archiving process.", e);
        }
        log.info("--- BATCHED WEEKLY DATABASE PRUNING FINISHED ---");
    }

    private void archiveJobsBatch(List<Job> jobs, ZipOutputStream zos) throws IOException {
        long timestamp = System.currentTimeMillis();
        
        ZipEntry jsonEntry = new ZipEntry("batch_" + timestamp + ".json");
        zos.putNextEntry(jsonEntry);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(zos, jobs);
        zos.closeEntry();

        ZipEntry csvEntry = new ZipEntry("batch_" + timestamp + ".csv");
        zos.putNextEntry(csvEntry);
        
        try (StringWriter stringWriter = new StringWriter();
             CSVWriter csvWriter = new CSVWriter(stringWriter)) {

            String[] header = {"id", "adzunaId", "title", "location", "country", "description", "url", "category", "createdAt", "experienceLevel", "companyName", "skills"};
            csvWriter.writeNext(header);

            for (Job job : jobs) {
                String[] data = {
                    job.getId() != null ? job.getId().toString() : "",
                    job.getAdzunaId(),
                    job.getTitle(),
                    job.getLocation(),
                    job.getCountry(),
                    job.getDescription(),
                    job.getUrl(),
                    job.getCategory(),
                    job.getCreatedAt() != null ? job.getCreatedAt().toString() : "",
                    job.getExperienceLevel(),
                    job.getCompanyName(),
                    job.getSkills() != null ? job.getSkills().stream().map(s -> s.getName()).collect(Collectors.joining(";")) : ""
                };
                csvWriter.writeNext(data);
            }
            csvWriter.close();

            zos.write(stringWriter.toString().getBytes(StandardCharsets.UTF_8));
        }
        zos.closeEntry();
    }

    public void restoreFromArchive(String archiveFileName) throws IOException {
        File archiveDir = new File("archives");
        File archiveFile = new File(archiveDir, archiveFileName);

        if (!archiveFile.exists()) {
            log.error("Archive file not found: {}", archiveFile.getAbsolutePath());
            throw new IOException("Archive file not found: " + archiveFileName);
        }

        log.info("Starting restoration from archive: {}", archiveFile.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(archiveFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".json") && entry.getName().startsWith("batch_")) {
                    log.info("Restoring from JSON batch: {}", entry.getName());
                    List<Job> restoredJobs = objectMapper.readValue(zis, new TypeReference<List<Job>>() {});

                    if (restoredJobs != null && !restoredJobs.isEmpty()) {
                        log.info("Found {} jobs in JSON batch. Checking for duplicates before saving...", restoredJobs.size());
                        
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
                            log.info("All {} jobs from the JSON batch already exist in the database. Nothing to restore.", restoredJobs.size());
                        }

                    } else {
                        log.warn("No jobs found inside the JSON batch: {}", entry.getName());
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            log.error("Failed to read or process the archive file: {}", archiveFile.getAbsolutePath(), e);
            throw e;
        }
    }
}
