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
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class JsearchIngestionService {

    private static final Logger log = LoggerFactory.getLogger(JsearchIngestionService.class);

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;
    private final WebSocketProgressService progressService;

    @Value("${jsearch.api.key}")
    private String API_KEY;

    private final String BASE_URL = "https://jsearch.p.rapidapi.com/search";

    public JsearchIngestionService(JobRepository jobRepository, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService, WebSocketProgressService progressService) {
        this.jobRepository = jobRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
        this.progressService = progressService;
    }

    @Async("taskExecutor")
    public void importJobs(String query, int numPages) {
        importJobsAndReportProgress(query, numPages);
    }

    @Async("taskExecutor")
    public void importJobsAndReportProgress(String query, int numPages) {
        log.info("JSearch - Starting real-time import for query: '{}', pages: {}", query, numPages);

        if (API_KEY == null || API_KEY.isEmpty() || API_KEY.contains("placeholder")) {
            log.error("JSearch - API Key is missing or invalid.");
            progressService.sendProgress("ERROR", "Configuration Error", "JSearch API Key is missing.");
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-rapidapi-key", API_KEY);
        headers.set("x-rapidapi-host", "jsearch.p.rapidapi.com");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
             String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());

             for (int page = 1; page <= numPages; page++) {
                try {
                    String url = String.format("%s?query=%s&page=%d&num_pages=1", BASE_URL, encodedQuery, page);
                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                    JsonNode data = objectMapper.readTree(response.getBody()).path("data");

                    if (data.isArray()) {
                        for (JsonNode jobNode : data) {
                            String jobTitle = jobNode.path("job_title").asText("Untitled Job");
                            progressService.sendProgress("PROCESSING", jobTitle, "Checking job details...");
                            
                            try {
                                Thread.sleep(200);
                                saveOrUpdateJob(jobNode);
                            } catch (Exception e) {
                                log.error("Error saving individual job: " + e.getMessage());
                                progressService.sendProgress("ERROR", jobTitle, e.getMessage());
                            }
                        }
                    }
                    if (page < numPages) Thread.sleep(1000);
                } catch (Exception e) {
                    log.error("JSearch - Error importing page {}: {}", page, e.getMessage());
                    progressService.sendProgress("ERROR", "API Call Failed", "Could not fetch data for page " + page);
                    if (e.getMessage().contains("429")) break; 
                    if (page < numPages) Thread.sleep(1000);
                }
            }
        } catch (Exception mainEx) {
             log.error("JSearch - Fatal error setting up the import request", mainEx);
             progressService.sendProgress("ERROR", "Fatal Error", mainEx.getMessage());
        }
        progressService.sendProgress("FINISHED", "Import Complete", "All pages have been processed.");
        log.info("JSearch - Real-time import finished for query: '{}'", query);
    }

    private void saveOrUpdateJob(JsonNode jobNode) {
        String jobId = jobNode.path("job_id").asText();
        String jobTitle = jobNode.path("job_title").asText("Untitled Job");

        if(jobId == null || jobId.isEmpty() || jobId.equals("null")) {
             progressService.sendProgress("SKIPPED", jobTitle, "Job has no ID.");
             return;
        }

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(jobId);
        if (existingJobOpt.isPresent()) {
            progressService.sendProgress("SKIPPED", jobTitle, "Job already exists in the database.");
            return;
        }

        String companyName = jobNode.path("employer_name").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);
        
        String url = jobNode.path("job_apply_link").asText("");
        String city = jobNode.path("job_city").asText("");
        String country = jobNode.path("job_country").asText("");
        String location = (city.isEmpty() || city.equals("null") ? "" : city + ", ") + country;
        if(location.isEmpty() || location.equals(", ") || location.equals("null")) location = "Unknown Location";
        
        String category = "IT/Software";
        String rawDescription = jobNode.path("job_description").asText("No description available.");
        String cleanDescription = Jsoup.parse(rawDescription).text();

        Job job = new Job(jobId, jobTitle, location, country, url, category, cleanDescription, company);

        if (jobNode.has("job_posted_at_datetime_utc") && !jobNode.get("job_posted_at_datetime_utc").isNull()) {
             try {
                String dateStr = jobNode.path("job_posted_at_datetime_utc").asText();
                if(dateStr.length() >= 19) {
                     job.setCreatedAt(LocalDateTime.parse(dateStr.substring(0, 19)));
                }
             } catch (Exception e) { /* ignore */ }
        }
        
        job.setCompanyName(company.getName());
        if (job.getCreatedAt() == null) job.setCreatedAt(LocalDateTime.now());

        Job savedJob = jobRepository.save(job);
        
        CompletableFuture<Job> futureJob = nerExtractionService.processJob(savedJob);
        futureJob.thenAccept(finalJob -> {
            String skills = finalJob.getSkills().stream().map(s -> s.getName()).collect(Collectors.joining(", "));
            progressService.sendProgress("SAVED", finalJob.getTitle(), "Skills: " + (skills.isEmpty() ? "None found" : skills));
        });
    }
}
