package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class ArbeitnowIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ArbeitnowIngestionService.class);

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;

    private final String BASE_URL = "https://www.arbeitnow.com/api/job-board-api";

    public ArbeitnowIngestionService(JobRepository jobRepository, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService) {
        this.jobRepository = jobRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
    }

    @Async("taskExecutor")
    public void importJobs(int startPage, int endPage) {
        log.info("Arbeitnow - Starting import from page {} to {}", startPage, endPage);

        for (int page = startPage; page <= endPage; page++) {
            try {
                String url = BASE_URL + "?page=" + page + "&search=it,software,developer,engineer,data";
                
                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                JsonNode results = root.path("data");

                if (results.isArray()) {
                    for (JsonNode jobNode : results) {
                        saveOrUpdateJob(jobNode);
                    }
                }

                Thread.sleep(1000);

            } catch (Exception e) {
                log.error("Arbeitnow - Error importing page {}: {}", page, e.getMessage());
            }
        }
        log.info("Arbeitnow - Import finished.");
    }

    private void saveOrUpdateJob(JsonNode jobNode) {
        String slug = jobNode.path("slug").asText();
        if (slug.isEmpty()) {
            log.warn("Arbeitnow - Skipped job with empty slug.");
            return;
        }

        String companyName = jobNode.path("company_name").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(slug);
        Job job;

        if (existingJobOpt.isPresent()) {
            job = existingJobOpt.get();
            job.setCompany(company);
        } else {
            String title = jobNode.path("title").asText();
            String url = jobNode.path("url").asText();
            String location = jobNode.path("location").asText("Unknown Location");
            String description = jobNode.path("description").asText("No description available.");

            job = new Job(slug, title, location, "Unknown", url, "IT/Software", description, company);
        }

        if (jobNode.has("created_at") && !jobNode.get("created_at").isNull()) {
            try {
                long timestamp = jobNode.path("created_at").asLong();
                LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC);
                job.setCreatedAt(ldt);
            } catch (Exception e) {
                log.warn("Arbeitnow - Could not parse created date for job {}: {}", slug, e.getMessage());
            }
        }

        job.setCompanyName(company.getName());

        if (job.getCreatedAt() == null) {
            job.setCreatedAt(LocalDateTime.now());
        }

        Job savedJob = jobRepository.save(job);
        nerExtractionService.processJob(savedJob);
    }
}
