package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.CompanyRepository;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
public class ArbeitnowIngestionService {

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

    public void importJobs(int startPage, int endPage) {
        System.out.println("Starting import from Arbeitnow from page " + startPage + " to " + endPage);

        for (int page = startPage; page <= endPage; page++) {
            try {
                String url = BASE_URL + "?page=" + page;
                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                JsonNode results = root.path("data");

                if (results.isArray()) {
                    for (JsonNode jobNode : results) {
                        saveJob(jobNode);
                    }
                }

                System.out.println("Imported page " + page + " from Arbeitnow");

                // Sleep to be gentle to the API
                Thread.sleep(1000);

            } catch (Exception e) {
                System.err.println("Error importing Arbeitnow page " + page + ": " + e.getMessage());
            }
        }
        System.out.println("Arbeitnow Import completed.");
    }

    private void saveJob(JsonNode jobNode) {
        // Arbeitnow uses "slug" or "id", we can use "slug" as unique id or combine with a prefix to avoid collisions
        String arbeitnowId = "arbeitnow_" + jobNode.path("slug").asText();
        
        // Skip if job already exists (re-using adzunaId field as a generic external id field)
        if (jobRepository.existsByAdzunaId(arbeitnowId)) {
            return;
        }

        String title = jobNode.path("title").asText();
        String url = jobNode.path("url").asText();
        
        // Description
        String description = jobNode.path("description").asText();
        if (description == null || description.trim().isEmpty() || description.equals("null")) {
             description = "No description available. Please visit the job link for more details.";
        }

        // Company
        String companyName = jobNode.path("company_name").asText();
        if (companyName == null || companyName.isEmpty() || companyName.equals("null")) {
             companyName = "Unknown Company";
        }
        Company company = findOrCreateCompany(companyName);

        // Location
        String location = jobNode.path("location").asText();
        if (location == null || location.isEmpty() || location.equals("null")) {
             location = "Unknown Location";
        }
        
        // Country
        // Arbeitnow doesn't always provide a distinct country field outside of location,
        // but let's try to extract it if available or infer from remote tag.
        String country = "Unknown Country";
        boolean isRemote = jobNode.path("remote").asBoolean(false);
        if (isRemote) {
            country = "Remote / Global";
        } else if (location.contains(",")) {
             // Heuristic: Often "City, Country"
             String[] parts = location.split(",");
             country = parts[parts.length - 1].trim();
        }

        // Category (Arbeitnow provides tags, we can take the first tag or default)
        String category = "Uncategorized";
        JsonNode tags = jobNode.path("tags");
        if (tags.isArray() && tags.size() > 0) {
            category = tags.get(0).asText();
        }

        Job job = new Job(arbeitnowId, title, location, country, url, category, description, company);
        job.setCompany(company);

        jobRepository.save(job);
    }

    private Company findOrCreateCompany(String name) {
        Optional<Company> existing = companyRepository.findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        Company newCompany = new Company(name);
        return companyRepository.save(newCompany);
    }
}