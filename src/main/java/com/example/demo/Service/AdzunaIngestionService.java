package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.CompanyRepository;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AdzunaIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AdzunaIngestionService.class);

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final BatchJobUpdateService batchJobUpdateService;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;

    @Value("${adzuna.api.id}")
    private String APP_ID;

    @Value("${adzuna.api.key}")
    private String APP_KEY;

    private final String BASE_URL = "https://api.adzuna.com/v1/api/jobs";

    public AdzunaIngestionService(JobRepository jobRepository, BatchJobUpdateService batchJobUpdateService, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService) {
        this.jobRepository = jobRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.batchJobUpdateService = batchJobUpdateService;
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
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

        log.info("Adzuna ({}) - Starting automatic batch update for remote status and contract type...", country.toUpperCase());
        batchJobUpdateService.updateAllJobsWithRemoteAndContractType();
    }

    private void saveOrUpdateJob(JsonNode jobNode, String countryCode) {
        String adzunaId = jobNode.path("id").asText();

        String companyName = jobNode.path("company").path("display_name").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(adzunaId);
        Job job;

        if (existingJobOpt.isPresent()) {
            job = existingJobOpt.get();
            job.setCompany(company);
        } else {
            String title = jobNode.path("title").asText();
            String url = jobNode.path("redirect_url").asText();
            String location = jobNode.path("location").path("display_name").asText("Unknown Location");
            String country = countryCode.toUpperCase();
            String category = jobNode.path("category").path("label").asText("Uncategorized");
            String description = jobNode.path("description").asText("No description available.");

            job = new Job(adzunaId, title, location, country, url, category, description, company);
        }

        if (jobNode.has("description") && !jobNode.get("description").isNull()) {
            job.setDescription(jobNode.path("description").asText());
        }
        if (jobNode.has("title") && !jobNode.get("title").isNull()) {
            job.setTitle(jobNode.path("title").asText());
        }

        if (jobNode.has("created") && !jobNode.get("created").isNull()) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(jobNode.path("created").asText());
                job.setCreatedAt(odt.toLocalDateTime());
            } catch (Exception e) {
                log.warn("Could not parse created date for job {}: {}", adzunaId, e.getMessage());
            }
        }

        job.setCompanyName(company.getName());

        if (job.getCreatedAt() == null) {
            job.setCreatedAt(java.time.LocalDateTime.now());
        }

        Job savedJob = jobRepository.save(job);
        
        nerExtractionService.processJob(savedJob);
    }
}
