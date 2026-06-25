package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ArbeitnowIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ArbeitnowIngestionService.class);

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;
    private final WebSocketProgressService progressService;

    private final String BASE_URL = "https://www.arbeitnow.com/api/job-board-api";

    public ArbeitnowIngestionService(JobRepository jobRepository, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService, WebSocketProgressService progressService) {
        this.jobRepository = jobRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
        this.progressService = progressService;
    }

    @Async("taskExecutor")
    public void importJobs(int startPage, int endPage) {
        log.info("Arbeitnow - Starting regular import from page {} to {}", startPage, endPage);
        importAndProcess(startPage, endPage, false);
    }

    @Async("taskExecutor")
    public void importJobsAndReportProgress(int numPages) {
        log.info("Arbeitnow - Starting demo import for {} pages", numPages);
        importAndProcess(1, numPages, true);
    }

    private void importAndProcess(int startPage, int endPage, boolean isDemo) {
        for (int page = startPage; page <= endPage; page++) {
            try {
                String url = BASE_URL + "?page=" + page + "&search=it,software,developer,engineer,data";
                
                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                JsonNode results = root.path("data");

                if (results.isArray()) {
                    for (JsonNode jobNode : results) {
                        if (isDemo) {
                            String jobTitle = jobNode.path("title").asText("Untitled Job");
                            progressService.sendProgress("PROCESSING", jobTitle, "Checking job details...");
                            Thread.sleep(200);
                        }
                        saveOrUpdateJob(jobNode, isDemo);
                    }
                }
                if (page < endPage) Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Arbeitnow - Error importing page {}: {}", page, e.getMessage());
                if (isDemo) progressService.sendProgress("ERROR", "API Call Failed", "Could not fetch data for page " + page);
            }
        }
        if (isDemo) progressService.sendProgress("FINISHED", "Import Complete", "All pages have been processed.");
        log.info("Arbeitnow - Import finished.");
    }

    private void saveOrUpdateJob(JsonNode jobNode, boolean runNer) {
        String slug = jobNode.path("slug").asText();
        String title = jobNode.path("title").asText("Untitled Job");

        if (slug.isEmpty()) {
            if(runNer) progressService.sendProgress("SKIPPED", title, "Job has no ID.");
            return;
        }

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(slug);
        if (existingJobOpt.isPresent()) {
            if(runNer) progressService.sendProgress("SKIPPED", title, "Job already exists.");
            return;
        }

        String companyName = jobNode.path("company_name").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        String url = jobNode.path("url").asText();
        String location = jobNode.path("location").asText("Unknown Location");
        String rawDescription = jobNode.path("description").asText("No description available.");
        String cleanDescription = Jsoup.parse(rawDescription).text();

        Job job = new Job(slug, title, location, "Unknown", url, "IT/Software", cleanDescription, company);
        
        if (jobNode.has("created_at") && !jobNode.get("created_at").isNull()) {
            try {
                long timestamp = jobNode.path("created_at").asLong();
                job.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC));
            } catch (Exception e) { /* ignore */ }
        }
        
        job.setCompanyName(company.getName());
        if (job.getCreatedAt() == null) job.setCreatedAt(LocalDateTime.now());

        Job savedJob = jobRepository.save(job);
        
        if (runNer) {
            CompletableFuture<Job> futureJob = nerExtractionService.processJob(savedJob);
            futureJob.thenAccept(finalJob -> {
                String skills = finalJob.getSkills().stream().map(s -> s.getName()).collect(Collectors.joining(", "));
                progressService.sendProgress("SAVED", finalJob.getTitle(), "Skills: " + (skills.isEmpty() ? "None found" : skills));
            });
        } else {
            log.info("Saved new Arbeitnow job ID {} without running NER.", savedJob.getId());
        }
    }
}
