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

    public HimalayasIngestionService(JobRepository jobRepository, RestTemplate restTemplate, ObjectMapper objectMapper, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService) {
        this.jobRepository = jobRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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
                    
                    // Increment offset by limit for next page
                    offset += limit;
                } else {
                    log.info("Himalayas - No more jobs found or empty response at offset {}", offset);
                    hasMore = false;
                }

                Thread.sleep(1500); // Rate limiting

            } catch (Exception e) {
                log.error("Himalayas - Error importing at offset {}: {}", offset, e.getMessage());
                // Break to avoid infinite loops on error, could be changed to retry logic
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
            
            // Himalayas usually provides countries or regions
            String location = "Remote"; 
            String category = "IT/Software"; // Default as mostly remote tech jobs
            String description = jobNode.path("description").asText("No description available.");
            if (jobNode.has("excerpt") && !jobNode.get("excerpt").isNull() && !jobNode.get("excerpt").asText().isEmpty()) {
                description = jobNode.get("excerpt").asText();
            }

            job = new Job(jobId, title, location, "Unknown", url, category, description, company);
        }

        // --- Update Fields ---
        if (jobNode.has("title") && !jobNode.get("title").isNull()) {
            job.setTitle(jobNode.path("title").asText());
        }
        if (jobNode.has("excerpt") && !jobNode.get("excerpt").isNull() && !jobNode.get("excerpt").asText().isEmpty()) {
            job.setDescription(jobNode.path("excerpt").asText());
        }
        
        // Himalayas jobs are remote by definition of the platform
        job.setJobIsRemote(true);

        if (jobNode.has("minSalary") && !jobNode.get("minSalary").isNull()) {
            job.setSalaryMin(jobNode.path("minSalary").asDouble());
        }
        if (jobNode.has("maxSalary") && !jobNode.get("maxSalary").isNull()) {
            job.setSalaryMax(jobNode.path("maxSalary").asDouble());
        }
        if (job.getSalaryMin() != null || job.getSalaryMax() != null) {
            job.setSalaryPeriod("year"); // Usually annual
        }

        // Determine experience level based on seniority if available
        if (jobNode.has("seniority") && !jobNode.get("seniority").isNull()) {
             job.setExperienceLevel(jobNode.path("seniority").asText());
        }
        
        // Contract type
        if (jobNode.has("employmentType") && !jobNode.get("employmentType").isNull()) {
             job.setContractType(jobNode.path("employmentType").asText());
        }

        // Date
        if (jobNode.has("pubDate") && !jobNode.get("pubDate").isNull()) {
             try {
                 // Format is usually unix timestamp
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