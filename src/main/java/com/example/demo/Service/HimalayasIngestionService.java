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
public class HimalayasIngestionService {

    private static final Logger log = LoggerFactory.getLogger(HimalayasIngestionService.class);

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;

    private final String BASE_URL = "https://himalayas.app/jobs/api";

    public HimalayasIngestionService(JobRepository jobRepository, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService) {
        this.jobRepository = jobRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
    }

    @Async("taskExecutor")
    public void importJobs(int limit, int initialOffset) {
        log.info("Himalayas - Starting import with limit {} and initial offset {}", limit, initialOffset);
        
        int offset = initialOffset;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String url = String.format("%s?limit=%d&offset=%d", BASE_URL, limit, offset);
                
                log.info("Himalayas - Fetching jobs at offset {}", offset);
                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                JsonNode jobsArray = root.path("jobs");

                if (jobsArray.isArray() && jobsArray.size() > 0) {
                    int savedCount = 0;
                    for (JsonNode jobNode : jobsArray) {
                        if (saveOrUpdateJob(jobNode)) {
                            savedCount++;
                        }
                    }
                    log.info("Himalayas - Saved/Updated {} jobs from offset {}", savedCount, offset);
                    
                    offset += limit;
                } else {
                    log.info("Himalayas - No more jobs found or empty response at offset {}", offset);
                    hasMore = false;
                }

                Thread.sleep(1500);

            } catch (Exception e) {
                log.error("Himalayas - Error importing at offset {}: {}", offset, e.getMessage());
                hasMore = false; 
            }
        }
        log.info("Himalayas - Import finished.");
    }

    private boolean saveOrUpdateJob(JsonNode jobNode) {
        String jobId = jobNode.path("id").asText();
        if (jobId.isEmpty() || jobId.equals("null")) {
            log.warn("Himalayas - Skipped job with missing ID");
            return false;
        }

        String companyName = jobNode.path("companyName").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(jobId);
        Job job;

        if (existingJobOpt.isPresent()) {
            job = existingJobOpt.get();
            job.setCompany(company);
        } else {
            String title = jobNode.path("title").asText("Untitled Job");
            String url = jobNode.path("applicationLink").asText();
            if (url.isEmpty() || url.equals("null")) {
                 url = jobNode.path("jobUri").asText("");
            }
            
            String location = "Remote"; 
            String category = "IT/Software";
            String description = jobNode.path("description").asText("No description available.");
            if (jobNode.has("excerpt") && !jobNode.get("excerpt").isNull() && !jobNode.get("excerpt").asText().isEmpty()) {
                description = jobNode.get("excerpt").asText();
            }

            job = new Job(jobId, title, location, "Unknown", url, category, description, company);
        }

        if (jobNode.has("pubDate") && !jobNode.get("pubDate").isNull()) {
             try {
                 long timestamp = jobNode.path("pubDate").asLong();
                 LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);
                 job.setCreatedAt(ldt);
             } catch (Exception e) {
                 // Ignore format issues
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
