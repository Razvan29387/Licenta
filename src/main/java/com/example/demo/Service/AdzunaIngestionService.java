package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AdzunaIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AdzunaIngestionService.class);

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final BatchJobUpdateService batchJobUpdateService;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;
    private final WebSocketProgressService progressService;

    @Value("${adzuna.api.id}")
    private String APP_ID;

    @Value("${adzuna.api.key}")
    private String APP_KEY;

    private final String BASE_URL = "https://api.adzuna.com/v1/api/jobs";

    public AdzunaIngestionService(JobRepository jobRepository, BatchJobUpdateService batchJobUpdateService, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService, WebSocketProgressService progressService) {
        this.jobRepository = jobRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.batchJobUpdateService = batchJobUpdateService;
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
        this.progressService = progressService;
    }

    @Async("taskExecutor")
    public void importJobs(String country, int startPage, int endPage) {
        log.info("Adzuna ({}) - Starting IT-only import from page {} to {}", country.toUpperCase(), startPage, endPage);
        // Regular import, no NER
        importAndProcess(country, "it-jobs", startPage, endPage, false);
        batchJobUpdateService.updateAllJobsWithRemoteAndContractType();
    }

    @Async("taskExecutor")
    public void importJobsAndReportProgress(String country, String keyword, int numPages) {
        log.info("Adzuna - Starting real-time import for keyword: '{}', pages: {}", keyword, numPages);
        // Demo import, with NER
        importAndProcess(country, keyword, 1, numPages, true);
    }

    private void importAndProcess(String country, String keyword, int startPage, int endPage, boolean isDemo) {
        if (APP_ID == null || APP_ID.isEmpty() || APP_KEY == null || APP_KEY.isEmpty()) {
            if (isDemo) progressService.sendProgress("ERROR", "Configuration Error", "Adzuna API credentials are missing.");
            log.error("Adzuna API credentials are missing.");
            return;
        }

        for (int page = startPage; page <= endPage; page++) {
            try {
                String url = String.format("%s/%s/search/%d?app_id=%s&app_key=%s&results_per_page=50&content-type=application/json&what=%s",
                        BASE_URL, country, page, APP_ID, APP_KEY, keyword);

                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                JsonNode results = root.path("results");

                if (results.isArray()) {
                    for (JsonNode jobNode : results) {
                        if (isDemo) {
                            String jobTitle = jobNode.path("title").asText("Untitled Job");
                            progressService.sendProgress("PROCESSING", jobTitle, "Checking job details...");
                            Thread.sleep(200);
                        }
                        saveOrUpdateJob(jobNode, country, isDemo);
                    }
                }
                if (page < endPage) Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Adzuna ({}) - Error importing page {}: {}", country.toUpperCase(), page, e.getMessage());
                if (isDemo) progressService.sendProgress("ERROR", "API Call Failed", "Could not fetch data for page " + page);
            }
        }
        if (isDemo) progressService.sendProgress("FINISHED", "Import Complete", "All pages have been processed.");
        log.info("Adzuna ({}) - Import finished for keyword '{}'.", country.toUpperCase(), keyword);
    }

    private void saveOrUpdateJob(JsonNode jobNode, String countryCode, boolean runNer) {
        String adzunaId = jobNode.path("id").asText();
        String title = jobNode.path("title").asText("Untitled Job");

        if(adzunaId == null || adzunaId.isEmpty() || adzunaId.equals("null")) {
             if(runNer) progressService.sendProgress("SKIPPED", title, "Job has no ID.");
             return;
        }

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(adzunaId);
        if (existingJobOpt.isPresent()) {
            if(runNer) progressService.sendProgress("SKIPPED", title, "Job already exists.");
            return;
        }

        String companyName = jobNode.path("company").path("display_name").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        String url = jobNode.path("redirect_url").asText();
        String location = jobNode.path("location").path("display_name").asText("Unknown Location");
        String country = countryCode.toUpperCase();
        String category = jobNode.path("category").path("label").asText("Uncategorized");
        String rawDescription = jobNode.path("description").asText("No description available.");
        String cleanDescription = Jsoup.parse(rawDescription).text();

        Job job = new Job(adzunaId, title, location, country, url, category, cleanDescription, company);

        if (jobNode.has("created") && !jobNode.get("created").isNull()) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(jobNode.path("created").asText());
                job.setCreatedAt(odt.toLocalDateTime());
            } catch (Exception e) { /* ignore */ }
        }

        job.setCompanyName(company.getName());
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(java.time.LocalDateTime.now());
        }

        Job savedJob = jobRepository.save(job);
        
        if (runNer) {
            CompletableFuture<Job> futureJob = nerExtractionService.processJob(savedJob);
            futureJob.thenAccept(finalJob -> {
                String skills = finalJob.getSkills().stream().map(s -> s.getName()).collect(Collectors.joining(", "));
                progressService.sendProgress("SAVED", title, "Skills: " + (skills.isEmpty() ? "None found" : skills));
            });
        } else {
            log.info("Saved new Adzuna job ID {} without running NER.", savedJob.getId());
        }
    }
}
