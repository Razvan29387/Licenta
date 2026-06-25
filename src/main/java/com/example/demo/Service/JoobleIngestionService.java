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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class JoobleIngestionService {

    private static final Logger log = LoggerFactory.getLogger(JoobleIngestionService.class);

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;
    private final WebSocketProgressService progressService;

    @Value("${jooble.api.key}")
    private String API_KEY;

    private final String BASE_URL = "https://jooble.org/api/";

    public JoobleIngestionService(JobRepository jobRepository, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService, WebSocketProgressService progressService) {
        this.jobRepository = jobRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
        this.progressService = progressService;
    }

    @Async("taskExecutor")
    public void importJobs(String keywords, String location, int numPages) {
        log.info("Jooble - Starting regular import for keywords: '{}', location: '{}', pages: {}", keywords, location, numPages);
        importAndProcess(keywords, location, numPages, false);
    }

    @Async("taskExecutor")
    public void importJobsAndReportProgress(String keywords, int numPages) {
        log.info("Jooble - Starting demo import for keywords: '{}', pages: {}", keywords, numPages);
        importAndProcess(keywords, null, numPages, true);
    }

    private void importAndProcess(String keywords, String location, int numPages, boolean isDemo) {
        if (API_KEY == null || API_KEY.isEmpty() || API_KEY.contains("placeholder")) {
            log.error("Jooble - API Key is missing or invalid.");
            if (isDemo) progressService.sendProgress("ERROR", "Configuration Error", "Jooble API Key is missing.");
            return;
        }

        String url = BASE_URL + API_KEY;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            for (int page = 1; page <= numPages; page++) {
                Map<String, Object> body = new HashMap<>();
                body.put("keywords", keywords != null && !keywords.isEmpty() ? keywords : "IT");
                if (location != null && !location.isEmpty()) body.put("location", location);
                body.put("page", String.valueOf(page));

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

                try {
                    log.info("Jooble - Fetching page {}/{}", page, numPages);
                    ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode jobs = root.path("jobs");

                    if (jobs.isArray()) {
                        for (JsonNode jobNode : jobs) {
                            if (isDemo) {
                                String jobTitle = jobNode.path("title").asText("Untitled Job");
                                progressService.sendProgress("PROCESSING", jobTitle, "Checking job details...");
                                Thread.sleep(200);
                            }
                            saveOrUpdateJob(jobNode, isDemo);
                        }
                    }
                    if (isDemo && page >= 1) break; // For demo, only process one page
                    Thread.sleep(1000); 
                } catch (Exception e) {
                    log.error("Jooble - Error importing page {}: {}", page, e.getMessage());
                    if (isDemo) progressService.sendProgress("ERROR", "API Call Failed", e.getMessage());
                    Thread.sleep(2000);
                }
            }
        } catch (Exception mainEx) {
            log.error("Jooble - Fatal error setting up the import request", mainEx);
            if (isDemo) progressService.sendProgress("ERROR", "Fatal Error", mainEx.getMessage());
        }
        if (isDemo) progressService.sendProgress("FINISHED", "Import Complete", "All pages have been processed.");
        log.info("Jooble - Import finished");
    }

    private void saveOrUpdateJob(JsonNode jobNode, boolean runNer) {
        String url = jobNode.path("link").asText("");
        String title = jobNode.path("title").asText("Untitled Job");

        if (url.isEmpty()) {
            if(runNer) progressService.sendProgress("SKIPPED", title, "Job has no URL.");
            return;
        }
        
        String jobId = jobNode.path("id").asText(String.valueOf(url.hashCode()));

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(jobId);
        if (existingJobOpt.isPresent()) {
            if(runNer) progressService.sendProgress("SKIPPED", title, "Job already exists.");
            return;
        }

        String companyName = jobNode.path("company").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        String jobLocation = jobNode.path("location").asText("Unknown Location");
        String category = "IT/Software";
        String rawDescription = jobNode.path("snippet").asText("No description available.");
        String cleanDescription = Jsoup.parse(rawDescription).text();

        Job job = new Job(jobId, title, jobLocation, "Unknown", url, category, cleanDescription, company);
        
        if (jobNode.has("updated") && !jobNode.get("updated").isNull()) {
            try {
                String dateStr = jobNode.path("updated").asText();
                job.setCreatedAt(LocalDateTime.parse(dateStr.substring(0, 19)));
            } catch (Exception e) { /* ignore */ }
        }

        job.setCompanyName(company.getName());
        if (job.getCreatedAt() == null) job.setCreatedAt(LocalDateTime.now());

        Job savedJob = jobRepository.save(job);
        
        if (runNer) {
            CompletableFuture<Job> futureJob = nerExtractionService.processJob(savedJob);
            futureJob.thenAccept(finalJob -> {
                String skills = finalJob.getSkills().stream().map(s -> s.getName()).collect(Collectors.joining(", "));
                progressService.sendProgress("SAVED", title, "Skills: " + (skills.isEmpty() ? "None found" : skills));
            });
        } else {
            log.info("Saved new Jooble job ID {} without running NER.", savedJob.getId());
        }
    }
}
