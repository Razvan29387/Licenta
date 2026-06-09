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

    @Transactional
    public void triggerSingleImportTask(String taskName) {
        log.info("Executing single task: {}", taskName);
        if ("jsearch-it".equalsIgnoreCase(taskName)) {
            jsearchIngestionService.importJobs("IT software developer engineer", 20);
        } else if ("remotive-software".equalsIgnoreCase(taskName)) {
            remotiveIngestionService.importJobs();
        } else if ("arbeitnow".equalsIgnoreCase(taskName)) {
            arbeitnowIngestionService.importJobs(1, ARBEITNOW_PAGES_TO_FETCH);
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
            adzunaIngestionService.importJobsAndReportProgress(countryCode, keywords, 1); // 1 page for demo
        } else if ("jsearch-it".equalsIgnoreCase(apiSource)) {
            jsearchIngestionService.importJobsAndReportProgress(keywords, 2); // 2 pages for demo
        } else if ("remotive-software".equalsIgnoreCase(apiSource)) {
            remotiveIngestionService.importJobsAndReportProgress(keywords);
        } else {
            log.warn("Unsupported API source for demo: {}", apiSource);
        }
    }

    @Scheduled(cron = "0 0 15 * * ?")
    public void triggerDailyFullDataImport() {
        // ... (code unchanged)
    }

    @Scheduled(cron = "0 0 5 * * SUN")
    @Transactional
    public void triggerWeeklyPruning() {
        // ... (code unchanged)
    }

    private void archiveJobs(List<Job> jobs) throws IOException {
        // ... (code unchanged)
    }

    public void restoreFromArchive(String archiveFileName) throws IOException {
        // ... (code unchanged)
    }
}
