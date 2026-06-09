package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;
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

        for (int page = startPage; page <= endPage; page++) {
            try {
                String url = String.format("%s/%s/search/%d?app_id=%s&app_key=%s&results_per_page=50&content-type=application/json&category=it-jobs",
                        BASE_URL, country, page, APP_ID, APP_KEY);

                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                JsonNode results = root.path("results");

                if (results.isArray()) {
                    for (JsonNode jobNode : results) {
                        saveOrUpdateJob(jobNode, country);
                    }
                }

                Thread.sleep(1000);

            } catch (Exception e) {
                log.error("Adzuna ({}) - Error importing page {}: {}", country.toUpperCase(), page, e.getMessage());
            }
        }
        log.info("Adzuna ({}) - Import finished.", country.toUpperCase());
        batchJobUpdateService.updateAllJobsWithRemoteAndContractType();
    }

    @Async("taskExecutor")
    public void importJobsAndReportProgress(String country, String keyword, int numPages) {
        log.info("Adzuna - Starting real-time import for keyword: '{}', pages: {}", keyword, numPages);

        if (APP_ID == null || APP_ID.isEmpty() || APP_KEY == null || APP_KEY.isEmpty()) {
            progressService.sendProgress("ERROR", "Configuration Error", "Adzuna API credentials are missing.");
            return;
        }

        try {
            for (int page = 1; page <= numPages; page++) {
                try {
                    // Note: Adzuna search uses 'what' parameter for keywords
                    String url = String.format("%s/%s/search/%d?app_id=%s&app_key=%s&results_per_page=20&content-type=application/json&what=%s",
                            BASE_URL, country, page, APP_ID, APP_KEY, keyword);

                    String jsonResponse = restTemplate.getForObject(url, String.class);
                    JsonNode root = objectMapper.readTree(jsonResponse);
                    JsonNode results = root.path("results");

                    if (results.isArray()) {
                        for (JsonNode jobNode : results) {
                            String jobTitle = jobNode.path("title").asText("Untitled Job");
                            progressService.sendProgress("PROCESSING", jobTitle, "Checking job details...");
                            
                            try {
                                Thread.sleep(500); 
                                saveOrUpdateJob(jobNode, country);
                            } catch (Exception e) {
                                progressService.sendProgress("ERROR", jobTitle, e.getMessage());
                            }
                        }
                    }
                    if (page < numPages) Thread.sleep(1000);
                } catch (Exception e) {
                    progressService.sendProgress("ERROR", "API Call Failed", "Could not fetch data for page " + page);
                    if (page < numPages) Thread.sleep(1000);
                }
            }
        } catch (Exception mainEx) {
             progressService.sendProgress("ERROR", "Fatal Error", mainEx.getMessage());
        }
        progressService.sendProgress("FINISHED", "Import Complete", "All pages have been processed.");
    }

    private void saveOrUpdateJob(JsonNode jobNode, String countryCode) {
        String adzunaId = jobNode.path("id").asText();
        String title = jobNode.path("title").asText("Untitled Job");

        if(adzunaId == null || adzunaId.isEmpty() || adzunaId.equals("null")) {
             progressService.sendProgress("SKIPPED", title, "Job has no ID.");
             return;
        }

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(adzunaId);
        if (existingJobOpt.isPresent()) {
            progressService.sendProgress("SKIPPED", title, "Job already exists.");
            return;
        }

        String companyName = jobNode.path("company").path("display_name").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        String url = jobNode.path("redirect_url").asText();
        String location = jobNode.path("location").path("display_name").asText("Unknown Location");
        String country = countryCode.toUpperCase();
        String category = jobNode.path("category").path("label").asText("Uncategorized");
        String description = jobNode.path("description").asText("No description available.");

        Job job = new Job(adzunaId, title, location, country, url, category, description, company);

        if (jobNode.has("created") && !jobNode.get("created").isNull()) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(jobNode.path("created").asText());
                job.setCreatedAt(odt.toLocalDateTime());
            } catch (Exception e) {
                // ignore
            }
        }

        job.setCompanyName(company.getName());
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(java.time.LocalDateTime.now());
        }

        Job savedJob = jobRepository.save(job);
        nerExtractionService.processJob(savedJob);
        
        Job finalJob = jobRepository.findById(savedJob.getId()).orElse(savedJob);
        String skills = finalJob.getSkills().stream().map(s -> s.getName()).collect(Collectors.joining(", "));
        progressService.sendProgress("SAVED", title, "Skills: " + (skills.isEmpty() ? "None found" : skills));
    }
}