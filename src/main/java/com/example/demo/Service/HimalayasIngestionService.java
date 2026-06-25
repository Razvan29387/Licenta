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
public class HimalayasIngestionService {

    private static final Logger log = LoggerFactory.getLogger(HimalayasIngestionService.class);
    private static final int MAX_OFFSET = 5000;

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;
    private final WebSocketProgressService progressService;

    private final String BASE_URL = "https://himalayas.app/jobs/api";

    public HimalayasIngestionService(JobRepository jobRepository, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService, WebSocketProgressService progressService) {
        this.jobRepository = jobRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
        this.progressService = progressService;
    }

    @Async("taskExecutor")
    public void importJobs(int limit, int initialOffset) {
        log.info("Himalayas - Starting regular import with limit {} and initial offset {}", limit, initialOffset);
        importAndProcess(limit, initialOffset, false);
    }

    @Async("taskExecutor")
    public void importJobsAndReportProgress(int limit) {
        log.info("Himalayas - Starting demo import with limit {}", limit);
        importAndProcess(limit, 0, true);
    }

    private void importAndProcess(int limit, int initialOffset, boolean isDemo) {
        int offset = initialOffset;
        boolean hasMore = true;

        while (hasMore && offset <= MAX_OFFSET) {
            try {
                String url = String.format("%s?limit=%d&offset=%d", BASE_URL, limit, offset);
                
                log.info("Himalayas - Fetching jobs at offset {}", offset);
                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                JsonNode jobsArray = root.path("jobs");

                if (jobsArray.isArray() && jobsArray.size() > 0) {
                    for (JsonNode jobNode : jobsArray) {
                        if (isDemo) {
                            String jobTitle = jobNode.path("title").asText("Untitled Job");
                            progressService.sendProgress("PROCESSING", jobTitle, "Checking job details...");
                            Thread.sleep(200);
                        }
                        saveOrUpdateJob(jobNode, isDemo);
                    }
                    offset += limit;
                } else {
                    log.info("Himalayas - No more jobs found at offset {}", offset);
                    hasMore = false;
                }
                if (isDemo) break; // For demo, only process one batch
                Thread.sleep(1500);
            } catch (Exception e) {
                log.error("Himalayas - Error importing at offset {}: {}", offset, e.getMessage());
                if (isDemo) progressService.sendProgress("ERROR", "API Call Failed", e.getMessage());
                hasMore = false; 
            }
        }
        
        if (isDemo) progressService.sendProgress("FINISHED", "Import Complete", "All jobs have been processed.");
        log.info("Himalayas - Import finished.");
    }

    private void saveOrUpdateJob(JsonNode jobNode, boolean runNer) {
        String jobId = jobNode.path("id").asText();
        String title = jobNode.path("title").asText("Untitled Job");

        if (jobId.isEmpty() || jobId.equals("null")) {
            if(runNer) progressService.sendProgress("SKIPPED", title, "Job has no ID.");
            return;
        }

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(jobId);
        if (existingJobOpt.isPresent()) {
            if(runNer) progressService.sendProgress("SKIPPED", title, "Job already exists.");
            return;
        }

        String companyName = jobNode.path("companyName").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        String url = jobNode.path("applicationLink").asText(jobNode.path("jobUri").asText(""));
        String location = "Remote"; 
        String category = "IT/Software";
        String rawDescription = jobNode.has("description") && !jobNode.get("description").isNull() ? jobNode.get("description").asText() : jobNode.get("excerpt").asText("No description");
        String cleanDescription = Jsoup.parse(rawDescription).text();

        Job job = new Job(jobId, title, location, "Unknown", url, category, cleanDescription, company);
        
        job.setJobIsRemote(true);
        if (jobNode.has("pubDate") && !jobNode.get("pubDate").isNull()) {
             try {
                 long timestamp = jobNode.path("pubDate").asLong();
                 job.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
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
            log.info("Saved new Himalayas job ID {} without running NER.", savedJob.getId());
        }
    }
}
