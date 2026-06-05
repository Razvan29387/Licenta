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

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RemotiveIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RemotiveIngestionService.class);

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;

    private final String BASE_URL = "https://remotive.com/api/remote-jobs?category=software-dev";

    public RemotiveIngestionService(JobRepository jobRepository, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService) {
        this.jobRepository = jobRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
    }

    @Async("taskExecutor")
    public void importJobs() {
        log.info("Remotive - Starting import for category 'software-dev'");

        try {
            String jsonResponse = restTemplate.getForObject(BASE_URL, String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode jobsNode = root.path("jobs");

            if (jobsNode.isArray()) {
                int savedCount = 0;
                for (JsonNode jobNode : jobsNode) {
                    if (saveOrUpdateJob(jobNode)) {
                        savedCount++;
                    }
                }
                log.info("Remotive - Saved/Updated {} jobs.", savedCount);
            } else {
                log.warn("Remotive - Received response without 'jobs' array.");
            }

        } catch (Exception e) {
            log.error("Remotive - Error during import: {}", e.getMessage(), e);
        }

        log.info("Remotive - Import finished.");
    }

    private boolean saveOrUpdateJob(JsonNode jobNode) {
        String jobId = jobNode.path("id").asText();

        if (jobId == null || jobId.isEmpty() || jobId.equals("null")) {
            log.warn("Remotive - Skipped job with missing ID");
            return false;
        }

        String companyName = jobNode.path("company_name").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(jobId);
        Job job;

        if (existingJobOpt.isPresent()) {
            job = existingJobOpt.get();
            job.setCompany(company);
        } else {
            String title = jobNode.path("title").asText("Untitled Job");
            String url = jobNode.path("url").asText("");
            
            String location = jobNode.path("candidate_required_location").asText("");
            if (location.isEmpty() || location.equals("null")) location = "Worldwide";
            
            String category = jobNode.path("category").asText("IT/Software");
            String description = jobNode.path("description").asText("No description available.");

            job = new Job(jobId, title, location, "Unknown", url, category, description, company);
        }

        if (jobNode.has("publication_date") && !jobNode.get("publication_date").isNull()) {
             try {
                String dateStr = jobNode.path("publication_date").asText();
                if(dateStr.length() >= 19) {
                     LocalDateTime ldt = LocalDateTime.parse(dateStr.substring(0, 19));
                     job.setCreatedAt(ldt);
                }
             } catch (Exception e) {
                 log.warn("Remotive - Could not parse publication date: {}", e.getMessage());
             }
        }

        job.setCompanyName(company.getName());

        if (job.getCreatedAt() == null) {
            job.setCreatedAt(LocalDateTime.now());
        }

        Job savedJob = jobRepository.save(job);
        nerExtractionService.processJob(savedJob);
        return true;
    }
}
