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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JoobleIngestionService {

    private static final Logger log = LoggerFactory.getLogger(JoobleIngestionService.class);

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;

    @Value("${jooble.api.key}")
    private String API_KEY;

    private final String BASE_URL = "https://jooble.org/api/";

    public JoobleIngestionService(JobRepository jobRepository, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService) {
        this.jobRepository = jobRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
    }

    @Async("taskExecutor")
    public void importJobs(String keywords, String location, int numPages) {
        log.info("Jooble - Starting import for keywords: '{}', location: '{}', pages: {}", keywords, location, numPages);

        if (API_KEY == null || API_KEY.isEmpty() || API_KEY.contains("placeholder")) {
            log.error("Jooble - API Key is missing or invalid.");
            return;
        }

        String url = BASE_URL + API_KEY;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            for (int page = 1; page <= numPages; page++) {
                Map<String, Object> body = new HashMap<>();
                if (keywords != null && !keywords.isEmpty()) {
                    body.put("keywords", keywords);
                } else {
                    body.put("keywords", "IT");
                }
                if (location != null && !location.isEmpty()) {
                    body.put("location", location);
                }
                body.put("page", String.valueOf(page));

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

                try {
                    log.info("Jooble - Fetching page {}/{}", page, numPages);
                    ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode jobs = root.path("jobs");

                    if (jobs.isArray()) {
                        int savedCount = 0;
                        for (JsonNode jobNode : jobs) {
                            if (saveOrUpdateJob(jobNode)) {
                                savedCount++;
                            }
                        }
                        log.info("Jooble - Saved/Updated {} jobs from page {}", savedCount, page);
                    } else {
                        log.warn("Jooble - Received response without 'jobs' array on page {}", page);
                    }

                    Thread.sleep(1000); 

                } catch (Exception e) {
                    log.error("Jooble - Error importing page {}: {}", page, e.getMessage());
                    Thread.sleep(2000);
                }
            }
        } catch (Exception mainEx) {
            log.error("Jooble - Fatal error setting up the import request", mainEx);
        }
        log.info("Jooble - Import finished");
    }

    private boolean saveOrUpdateJob(JsonNode jobNode) {
        String url = jobNode.path("link").asText("");
        if (url == null || url.isEmpty() || url.equals("null")) {
            return false;
        }
        
        String jobId = jobNode.path("id").asText();
        if (jobId.isEmpty() || jobId.equals("null")) {
            jobId = String.valueOf(url.hashCode());
        }

        String companyName = jobNode.path("company").asText("Unknown Company").trim();
        if (companyName.isEmpty() || companyName.equals("null")) {
            companyName = "Unknown Company";
        }
        
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(jobId);
        Job job;

        if (existingJobOpt.isPresent()) {
            job = existingJobOpt.get();
            job.setCompany(company);
        } else {
            String title = jobNode.path("title").asText("Untitled Job");
            String jobLocation = jobNode.path("location").asText("Unknown Location");
            String category = "IT/Software";
            String rawDescription = jobNode.path("snippet").asText("No description available.");
            String cleanDescription = Jsoup.parse(rawDescription).text();

            job = new Job(jobId, title, jobLocation, "Unknown", url, category, cleanDescription, company);
        }

        if (jobNode.has("title") && !jobNode.get("title").isNull()) {
            job.setTitle(jobNode.path("title").asText());
        }
        if (jobNode.has("snippet") && !jobNode.get("snippet").isNull()) {
            job.setDescription(Jsoup.parse(jobNode.path("snippet").asText()).text());
        }
        if (jobNode.has("type") && !jobNode.get("type").isNull()) {
            job.setContractType(jobNode.path("type").asText());
        }
        if (jobNode.has("salary") && !jobNode.get("salary").isNull()) {
            String salaryStr = jobNode.path("salary").asText();
            if (!salaryStr.isEmpty() && !salaryStr.equals("null")) {
                job.setSalaryPeriod(salaryStr); 
            }
        }
        if (jobNode.has("updated") && !jobNode.get("updated").isNull()) {
            try {
                String dateStr = jobNode.path("updated").asText();
                if (dateStr.length() >= 19) {
                    LocalDateTime ldt = LocalDateTime.parse(dateStr.substring(0, 19));
                    job.setCreatedAt(ldt);
                }
            } catch (Exception e) {
                // ignore date parse error
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
