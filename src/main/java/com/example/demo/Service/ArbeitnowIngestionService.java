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

import java.util.Optional;

@Service
public class ArbeitnowIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ArbeitnowIngestionService.class);

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final String BASE_URL = "https://arbeitnow.com/api/job-board-api";

    public ArbeitnowIngestionService(JobRepository jobRepository, CompanyRepository companyRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Async("taskExecutor") // Rulează această metodă în pool-ul de thread-uri definit
    public void importJobs(int startPage, int endPage) {
        log.info("Arbeitnow - Starting IT-only import from page {} to {}", startPage, endPage);

        for (int page = startPage; page <= endPage; page++) {
            try {
                // Added '&search=...' to filter for technology-related jobs
                String url = BASE_URL + "?page=" + page + "&search=it,software,developer,engineer,data";
                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                JsonNode results = root.path("data");

                if (results.isArray()) {
                    for (JsonNode jobNode : results) {
                        saveJob(jobNode);
                    }
                }

                // Sleep to be gentle to the API
                Thread.sleep(1000);

            } catch (Exception e) {
                log.error("Arbeitnow - Error importing page {}: {}", page, e.getMessage());
            }
        }
        log.info("Arbeitnow - Import finished.");
    }

    private void saveJob(JsonNode jobNode) {
        String arbeitnowId = "arbeitnow_" + jobNode.path("slug").asText();
        
        if (jobRepository.existsByAdzunaId(arbeitnowId)) {
            return;
        }

        String title = jobNode.path("title").asText();
        String url = jobNode.path("url").asText();
        
        String rawDescription = jobNode.path("description").asText();
        String description = "No description available.";
        if (rawDescription != null && !rawDescription.trim().isEmpty() && !rawDescription.equals("null")) {
            description = rawDescription.replaceAll("<[^>]*>", "").trim(); // Simplificat
        }

        String companyName = jobNode.path("company_name").asText("Unknown Company");
        
        // Normalize company name (trim spaces)
        if (companyName != null) {
             companyName = companyName.trim();
        }

        Company company = findOrCreateCompany(companyName);

        String location = jobNode.path("location").asText("Unknown Location");
        
        String country = "Unknown";
        if (jobNode.path("remote").asBoolean(false)) {
            country = "Remote";
        } else if (location.contains(",")) {
             String[] parts = location.split(",");
             country = parts[parts.length - 1].trim();
        }

        String category = "General";
        JsonNode tags = jobNode.path("tags");
        if (tags.isArray() && tags.size() > 0) {
            category = tags.get(0).asText();
        }

        Job job = new Job(arbeitnowId, title, location, country, url, category, description, company);
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