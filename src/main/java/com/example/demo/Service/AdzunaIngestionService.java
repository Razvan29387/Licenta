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
public class AdzunaIngestionService {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Replace with your actual credentials
    private final String APP_ID = "ef9d97f4";
    private final String APP_KEY = "da6e99c85b206a161efb673b1e8c25fa";
    private final String BASE_URL = "https://api.adzuna.com/v1/api/jobs";

    public AdzunaIngestionService(JobRepository jobRepository, CompanyRepository companyRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public void importJobs(String country, int startPage, int endPage) {
        System.out.println("Starting import for " + country + " from page " + startPage + " to " + endPage);

        for (int page = startPage; page <= endPage; page++) {
            try {
                String url = String.format("%s/%s/search/%d?app_id=%s&app_key=%s&results_per_page=50&content-type=application/json",
                        BASE_URL, country, page, APP_ID, APP_KEY);

                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                JsonNode results = root.path("results");

                if (results.isArray()) {
                    for (JsonNode jobNode : results) {
                        saveJob(jobNode, country); // Pass the country here
                    }
                }

                System.out.println("Imported page " + page);
                
                // Sleep to respect API rate limits (very important!)
                Thread.sleep(1000); 

            } catch (Exception e) {
                System.err.println("Error importing page " + page + ": " + e.getMessage());
            }
        }
        System.out.println("Import completed.");
    }

    private void saveJob(JsonNode jobNode, String countryCode) {
        String adzunaId = jobNode.path("id").asText();
        
        // Skip if job already exists
        if (jobRepository.existsByAdzunaId(adzunaId)) {
            return;
        }

        String title = jobNode.path("title").asText();
        String url = jobNode.path("redirect_url").asText();
        
        // Extract Description
        String description = jobNode.path("description").asText();
        
        // Check if description is null or empty
        if (description == null || description.trim().isEmpty() || description.equals("null")) {
             description = "No description available. Please visit the job link for more details.";
        }

        // Extract Company
        String companyName = jobNode.path("company").path("display_name").asText();
        if (companyName == null || companyName.isEmpty() || companyName.equals("null")) {
             // Fallback for company name if display_name is missing or null
             companyName = jobNode.path("company").path("name").asText("Unknown Company");
        }
        // IMPORTANT: Fetch the managed entity from DB to ensure relationships are created correctly
        Company company = findOrCreateCompany(companyName);

        // Extract Location
        String location = jobNode.path("location").path("display_name").asText();
        if (location == null || location.isEmpty() || location.equals("null")) {
             location = jobNode.path("location").asText("Unknown Location"); // Fallback to direct location field
        }
        
        // Use the country code from the loop, make it uppercase for better readability
        String country = countryCode.toUpperCase();
        
        // Extract Category
        String category = jobNode.path("category").path("label").asText();
        if (category == null || category.isEmpty() || category.equals("null")) {
             category = jobNode.path("category").asText("Uncategorized"); // Fallback to direct category field
        }

        Job job = new Job(adzunaId, title, location, country, url, category, description, company);
        
        // Extract Salary details from Adzuna API
        if (jobNode.has("salary_min") && !jobNode.path("salary_min").isNull()) {
            job.setSalaryMin(jobNode.path("salary_min").asDouble());
        }
        if (jobNode.has("salary_max") && !jobNode.path("salary_max").isNull()) {
            job.setSalaryMax(jobNode.path("salary_max").asDouble());
        }
        
        // Adzuna mainly returns annual salaries, but sometimes indicates "is_hourly" or similar. 
        // We'll set a default of "year" if salary exists, unless we detect otherwise.
        if (job.getSalaryMin() != null || job.getSalaryMax() != null) {
             job.setSalaryPeriod("year"); // Default Adzuna assumption
        }
        
        // Explicitly set the company again, just to be sure (though constructor should handle it)
        job.setCompany(company);

        jobRepository.save(job);
    }

    private Company findOrCreateCompany(String name) {
        Optional<Company> existing = companyRepository.findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        Company newCompany = new Company(name);
        // Save first to get an ID and ensure it exists as a node
        return companyRepository.save(newCompany);
    }
}