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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class JsearchIngestionService {

    private static final Logger log = LoggerFactory.getLogger(JsearchIngestionService.class);

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final BatchJobUpdateService batchJobUpdateService;
    private final EntityResolutionService entityResolutionService;

    @Value("${jsearch.api.key}")
    private String API_KEY;

    @Value("${ingestion.sources.jsearch.default-query:developer}")
    private String defaultQuery;

    private final String BASE_URL = "https://jsearch.p.rapidapi.com/search";

    public JsearchIngestionService(JobRepository jobRepository, CompanyRepository companyRepository, BatchJobUpdateService batchJobUpdateService, EntityResolutionService entityResolutionService) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.batchJobUpdateService = batchJobUpdateService;
        this.entityResolutionService = entityResolutionService;
    }

    @Async("taskExecutor")
    public void importJobs(String query, int numPages) {
        log.info("JSearch - Starting import for query: '{}', pages: {}", query, numPages);

        if (API_KEY == null || API_KEY.isEmpty() || API_KEY.contains("placeholder")) {
            log.error("JSearch - API Key is missing or invalid. Please check your application.properties or environment variables.");
            return; // Oprește execuția dacă nu avem cheie validă
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-rapidapi-key", API_KEY);
        headers.set("x-rapidapi-host", "jsearch.p.rapidapi.com");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
             // Encode the query string to handle spaces and special characters properly
             String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());

             for (int page = 1; page <= numPages; page++) {
                try {
                    String url = String.format("%s?query=%s&page=%d&num_pages=1", BASE_URL, encodedQuery, page);

                    log.info("JSearch - Fetching page {}/{}", page, numPages);
                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode data = root.path("data");

                    if (data.isArray()) {
                        int savedCount = 0;
                        for (JsonNode jobNode : data) {
                            if(saveOrUpdateJob(jobNode)) {
                                savedCount++;
                            }
                        }
                        log.info("JSearch - Saved/Updated {} jobs from page {}", savedCount, page);
                    } else {
                        log.warn("JSearch - Received response without 'data' array on page {}", page);
                    }

                    Thread.sleep(3700);

                } catch (Exception e) {
                    log.error("JSearch - Error importing page {}: {}", page, e.getMessage());
                    // Dacă primim eroare 429 sau 403, e mai sigur să așteptăm mai mult sau să ne oprim
                    if (e.getMessage().contains("429") || e.getMessage().contains("403")) {
                        log.warn("JSearch - Rate limit or auth error detected. Stopping import task early.");
                        break; // Ieșim din buclă pentru a nu consuma inutil request-uri eșuate
                    }
                    Thread.sleep(3700); // Wait even on error before next retry
                }
            }
        } catch (Exception mainEx) {
             log.error("JSearch - Fatal error setting up the import request", mainEx);
        }
        log.info("JSearch - Import finished for query: '{}'", query);
    }

    private boolean saveOrUpdateJob(JsonNode jobNode) {
        String jobId = jobNode.path("job_id").asText();
        
        if(jobId == null || jobId.isEmpty() || jobId.equals("null")) {
             log.warn("JSearch - Skipped job with missing ID");
             return false;
        }

        String companyName = jobNode.path("employer_name").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);
        

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(jobId);
        Job job;

        if (existingJobOpt.isPresent()) {
            job = existingJobOpt.get();
            job.setCompany(company);
        } else {
            String title = jobNode.path("job_title").asText("Untitled Job");
            String url = jobNode.path("job_apply_link").asText("");
            String city = jobNode.path("job_city").asText("");
            String country = jobNode.path("job_country").asText("");
            String location = (city.isEmpty() || city.equals("null") ? "" : city + ", ") + country;
            if(location.isEmpty() || location.equals(", ") || location.equals("null")) location = "Unknown Location";
            
            String category = "IT/Software";
            String description = jobNode.path("job_description").asText("No description available.");

            job = new Job(jobId, title, location, country, url, category, description, company);
        }

        // --- UPDATE ALL FIELDS ---
        if (jobNode.has("job_description") && !jobNode.get("job_description").isNull()) {
            job.setDescription(jobNode.path("job_description").asText());
        }
        if (jobNode.has("job_title") && !jobNode.get("job_title").isNull()) {
            job.setTitle(jobNode.path("job_title").asText());
        }

        if (jobNode.has("job_employment_type") && !jobNode.get("job_employment_type").isNull()) {
            job.setContractType(jobNode.path("job_employment_type").asText());
        }

        if (jobNode.has("job_is_remote") && !jobNode.get("job_is_remote").isNull()) {
            job.setJobIsRemote(jobNode.path("job_is_remote").asBoolean());
        }

        if (jobNode.has("job_min_salary") && !jobNode.get("job_min_salary").isNull()) {
            job.setSalaryMin(jobNode.path("job_min_salary").asDouble());
        }
        if (jobNode.has("job_max_salary") && !jobNode.get("job_max_salary").isNull()) {
            job.setSalaryMax(jobNode.path("job_max_salary").asDouble());
        }
        if (jobNode.has("job_salary_currency") && !jobNode.get("job_salary_currency").isNull()) {
             job.setSalaryPeriod(jobNode.path("job_salary_currency").asText()); 
        }

        if (jobNode.has("job_required_experience") && !jobNode.get("job_required_experience").isNull()) {
            JsonNode expNode = jobNode.get("job_required_experience");
            if (expNode.has("required_experience_in_months") && !expNode.get("required_experience_in_months").isNull()) {
                int months = expNode.get("required_experience_in_months").asInt();
                if (months > 0) {
                    job.setExperienceLevel(months + " months");
                }
            }
        }

        if (jobNode.has("job_posted_at_datetime_utc") && !jobNode.get("job_posted_at_datetime_utc").isNull()) {
             try {
                String dateStr = jobNode.path("job_posted_at_datetime_utc").asText();
                if(dateStr.length() >= 19) {
                     LocalDateTime ldt = LocalDateTime.parse(dateStr.substring(0, 19));
                     job.setCreatedDate(ldt);
                }
             } catch (Exception e) {
                 log.warn("JSearch - Could not parse posted date: {}", e.getMessage());
             }
        }
        
        if (jobNode.has("job_latitude") && !jobNode.get("job_latitude").isNull()) {
            job.setLatitude(jobNode.path("job_latitude").asDouble());
        }
        if (jobNode.has("job_longitude") && !jobNode.get("job_longitude").isNull()) {
            job.setLongitude(jobNode.path("job_longitude").asDouble());
        }

        job.setCompanyName(company.getName());

        if (job.getCreatedAt() == null) {
            job.setCreatedAt(LocalDateTime.now());
        }

        jobRepository.save(job);
        return true;
    }
}