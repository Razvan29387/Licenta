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

import java.util.Optional;

@Service
public class AdzunaIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AdzunaIngestionService.class);

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${adzuna.api.id}")
    private String APP_ID;

    @Value("${adzuna.api.key}")
    private String APP_KEY;

    private final String BASE_URL = "https://api.adzuna.com/v1/api/jobs";

    public AdzunaIngestionService(JobRepository jobRepository, CompanyRepository companyRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Async("taskExecutor") // Rulează această metodă în pool-ul de thread-uri definit
    public void importJobs(String country, int startPage, int endPage) {
        log.info("Adzuna ({}) - Starting IT-only import from page {} to {}", country.toUpperCase(), startPage, endPage);

        for (int page = startPage; page <= endPage; page++) {
            try {
                // Added '&category=it-jobs' to filter for technology-related jobs
                String url = String.format("%s/%s/search/%d?app_id=%s&app_key=%s&results_per_page=50&content-type=application/json&category=it-jobs",
                        BASE_URL, country, page, APP_ID, APP_KEY);

                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                JsonNode results = root.path("results");

                if (results.isArray()) {
                    for (JsonNode jobNode : results) {
                        saveJob(jobNode, country);
                    }
                }
                
                // Sleep to respect API rate limits (very important!)
                Thread.sleep(1000); // 1 secundă pauză între request-uri

            } catch (Exception e) {
                log.error("Adzuna ({}) - Error importing page {}: {}", country.toUpperCase(), page, e.getMessage());
            }
        }
        log.info("Adzuna ({}) - Import finished.", country.toUpperCase());
    }

    private void saveJob(JsonNode jobNode, String countryCode) {
        String adzunaId = jobNode.path("id").asText();
        
        if (jobRepository.existsByAdzunaId(adzunaId)) {
            return;
        }

        String title = jobNode.path("title").asText();
        String url = jobNode.path("redirect_url").asText();
        String description = jobNode.path("description").asText("No description available.");

        String companyName = jobNode.path("company").path("display_name").asText("Unknown Company");
        
        // Normalize company name (trim spaces, lowercase)
        if (companyName != null) {
             companyName = companyName.trim();
        }

        Company company = findOrCreateCompany(companyName);

        String location = jobNode.path("location").path("display_name").asText("Unknown Location");
        String country = countryCode.toUpperCase();
        String category = jobNode.path("category").path("label").asText("Uncategorized");

        Job job = new Job(adzunaId, title, location, country, url, category, description, company);
        
        if (jobNode.has("salary_min")) job.setSalaryMin(jobNode.path("salary_min").asDouble());
        if (jobNode.has("salary_max")) job.setSalaryMax(jobNode.path("salary_max").asDouble());
        if (job.getSalaryMin() != null || job.getSalaryMax() != null) {
             job.setSalaryPeriod("year");
        }
        
        job.setCompany(company);
        jobRepository.save(job);
    }

    @Transactional
    protected Company findOrCreateCompany(String name) {
        // Neo4j lock is acquired or handled safely within @Transactional for concurrency
        return companyRepository.findByName(name)
                .orElseGet(() -> companyRepository.save(new Company(name)));
    }
}