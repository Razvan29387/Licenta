package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.CompanyRepository;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class RemotiveIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RemotiveIngestionService.class);

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;

    // Folosim categoria "software-dev" pentru a aduce doar joburi IT
    private final String BASE_URL = "https://remotive.com/api/remote-jobs?category=software-dev";

    public RemotiveIngestionService(JobRepository jobRepository, RestTemplate restTemplate, ObjectMapper objectMapper, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService) {
        this.jobRepository = jobRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
    }

    /**
     * Importă toate joburile (API-ul Remotive returnează toate joburile
     * pentru categoria respectivă într-un singur request, nu are paginare
     * în acest endpoint).
     */
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
            
            // Candidate required location (poate fi gol, "Worldwide", etc.)
            String location = jobNode.path("candidate_required_location").asText("");
            if (location.isEmpty() || location.equals("null")) location = "Worldwide";
            
            String category = jobNode.path("category").asText("IT/Software");
            String description = jobNode.path("description").asText("No description available.");

            job = new Job(jobId, title, location, "Unknown", url, category, description, company);
        }

        // --- UPDATE CÂMPURI ---
        if (jobNode.has("description") && !jobNode.get("description").isNull()) {
            job.setDescription(jobNode.path("description").asText());
        }
        if (jobNode.has("title") && !jobNode.get("title").isNull()) {
            job.setTitle(jobNode.path("title").asText());
        }
        if (jobNode.has("candidate_required_location") && !jobNode.get("candidate_required_location").isNull()) {
             job.setLocation(jobNode.path("candidate_required_location").asText());
        }

        if (jobNode.has("job_type") && !jobNode.get("job_type").isNull()) {
            // Remotive returnează "full_time", "contract", etc. Le putem curăța.
            String type = jobNode.path("job_type").asText().replace("_", " ");
            job.setContractType(type);
        }

        if (jobNode.has("salary") && !jobNode.get("salary").isNull() && !jobNode.get("salary").asText().isEmpty()) {
            job.setSalaryPeriod(jobNode.path("salary").asText()); // Remotive dă salariul ca un string "100k - 120k"
        }

        // Remotive jobs sunt remote.
        job.setJobIsRemote(true);

        if (jobNode.has("publication_date") && !jobNode.get("publication_date").isNull()) {
             try {
                // "2024-05-19T06:40:27"
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