package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.CompanyRepository;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class HimalayasIngestionService {

    private static final Logger log = LoggerFactory.getLogger(HimalayasIngestionService.class);

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String BASE_URL = "https://himalayas.app/jobs/api";

    public HimalayasIngestionService(JobRepository jobRepository, CompanyRepository companyRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Async("taskExecutor")
    public void importJobs(int limit, int offset) {
        log.info("Himalayas - Starting import limit: {}, offset: {}", limit, offset);

        try {
            String url = String.format("%s?limit=%d&offset=%d", BASE_URL, limit, offset);
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);

            if (responseEntity.getBody() == null) {
                log.warn("Himalayas - Received empty response");
                return;
            }

            JsonNode root = objectMapper.readTree(responseEntity.getBody());
            JsonNode jobsArray = root.path("jobs");

            if (jobsArray.isArray()) {
                int savedCount = 0;
                int skippedCount = 0;
                for (JsonNode jobNode : jobsArray) {
                    if (isEngineeringJob(jobNode)) {
                        if (saveOrUpdateJob(jobNode)) {
                            savedCount++;
                        }
                    } else {
                        skippedCount++;
                        String title = jobNode.path("title").asText("Unknown Title");
                        log.debug("Himalayas - Skipped non-engineering job: {}", title);
                    }
                }
                log.info("Himalayas - Saved/Updated {} jobs (skipped {} non-engineering jobs)", savedCount, skippedCount);
            }

        } catch (Exception e) {
            log.error("Himalayas - Error importing: {}", e.getMessage(), e);
        }
    }

    private boolean isEngineeringJob(JsonNode jobNode) {
        if (jobNode.has("categories") && jobNode.get("categories").isArray()) {
            for (JsonNode catNode : jobNode.get("categories")) {
                String cat = catNode.asText().toLowerCase();
                if (cat.contains("engineer") || cat.contains("software") || cat.contains("developer") || cat.contains("programming") || cat.contains("backend") || cat.contains("frontend") || cat.contains("fullstack") || cat.contains("data") || cat.contains("cloud") || cat.contains("devops")) {
                    return true;
                }
            }
        }
        
        String title = jobNode.path("title").asText("").toLowerCase();
        if (title.contains("engineer") || title.contains("developer") || title.contains("software") || title.contains("programmer") || title.contains("architect") || title.contains("data") || title.contains("cloud") || title.contains("devops") || title.contains("qa ") || title.contains("sdet")) {
            return true;
        }

        return false;
    }

    private boolean saveOrUpdateJob(JsonNode jobNode) {
        String jobId = jobNode.path("id").asText();
        String externalId = jobNode.path("guid").asText();
        
        if (jobId == null || jobId.isEmpty() || jobId.equals("null")) {
            jobId = externalId;
        }

        if (jobId == null || jobId.isEmpty() || jobId.equals("null")) {
             log.warn("Himalayas - Skipped job with missing ID");
             return false;
        }

        String companyName = jobNode.path("companyName").asText("Unknown Company").trim();
        Company company = findOrCreateCompany(companyName);

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId("himalayas-" + jobId);
        Job job;

        if (existingJobOpt.isPresent()) {
            job = existingJobOpt.get();
            job.setCompany(company);
        } else {
            String title = jobNode.path("title").asText("Untitled Job");
            String url = jobNode.path("applicationLink").asText();
            if (url == null || url.isEmpty() || url.equals("null")) {
                url = jobNode.path("url").asText("");
            }

            String location = "Remote";
            String country = "";
            
            // Collect categories if present
            String category = "IT/Software";
            if (jobNode.has("categories") && jobNode.get("categories").isArray()) {
                StringBuilder catBuilder = new StringBuilder();
                for (JsonNode catNode : jobNode.get("categories")) {
                    catBuilder.append(catNode.asText()).append(", ");
                }
                if (!catBuilder.isEmpty()) {
                    category = catBuilder.substring(0, catBuilder.length() - 2);
                }
            }

            String description = jobNode.path("description").asText("No description available.");

            job = new Job("himalayas-" + jobId, title, location, country, url, category, description, company);
        }

        // --- UPDATE ALL FIELDS ---
        job.setJobIsRemote(true); // All jobs on Himalayas are remote

        if (jobNode.has("description") && !jobNode.get("description").isNull()) {
            job.setDescription(jobNode.path("description").asText());
        }
        if (jobNode.has("title") && !jobNode.get("title").isNull()) {
            job.setTitle(jobNode.path("title").asText());
        }

        if (jobNode.has("minSalary") && !jobNode.get("minSalary").isNull()) {
            job.setSalaryMin(jobNode.path("minSalary").asDouble());
        }
        if (jobNode.has("maxSalary") && !jobNode.get("maxSalary").isNull()) {
            job.setSalaryMax(jobNode.path("maxSalary").asDouble());
        }
        if (jobNode.has("salaryCurrency") && !jobNode.get("salaryCurrency").isNull()) {
            job.setSalaryPeriod(jobNode.path("salaryCurrency").asText()); // using period for currency
        }

        if (jobNode.has("pubDate") && !jobNode.get("pubDate").isNull()) {
             try {
                long pubDateTimestamp = jobNode.path("pubDate").asLong();
                if (pubDateTimestamp > 0) {
                    LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(pubDateTimestamp), ZoneId.systemDefault());
                    job.setCreatedDate(ldt);
                }
             } catch (Exception e) {
                 log.warn("Himalayas - Could not parse posted date: {}", e.getMessage());
             }
        }
        
        job.setCompanyName(companyName);

        if (job.getCreatedAt() == null) {
            job.setCreatedAt(LocalDateTime.now());
        }

        jobRepository.save(job);
        return true;
    }

    @Transactional
    protected Company findOrCreateCompany(String name) {
        return companyRepository.findByName(name)
                .orElseGet(() -> companyRepository.save(new Company(name)));
    }
}