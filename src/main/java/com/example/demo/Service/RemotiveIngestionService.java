package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RemotiveIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RemotiveIngestionService.class);

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;
    private final WebSocketProgressService progressService;

    // Folosim categoria "software-dev" pentru a aduce doar joburi IT
    private final String BASE_URL = "https://remotive.com/api/remote-jobs?search=";

    public RemotiveIngestionService(JobRepository jobRepository, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService, WebSocketProgressService progressService) {
        this.jobRepository = jobRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
        this.progressService = progressService;
    }

    @Async("taskExecutor")
    public void importJobs() {
        log.info("Remotive - Starting import for category 'software-dev'");
        try {
            String jsonResponse = restTemplate.getForObject("https://remotive.com/api/remote-jobs?category=software-dev", String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode jobsNode = root.path("jobs");

            if (jobsNode.isArray()) {
                for (JsonNode jobNode : jobsNode) {
                    saveOrUpdateJob(jobNode);
                }
            }
        } catch (Exception e) {
            log.error("Remotive - Error during import: {}", e.getMessage(), e);
        }
        log.info("Remotive - Import finished.");
    }

    @Async("taskExecutor")
    public void importJobsAndReportProgress(String keyword) {
        log.info("Remotive - Starting real-time import for keyword: '{}'", keyword);

        try {
            String url = BASE_URL + keyword;
            String jsonResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode jobsNode = root.path("jobs");

            if (jobsNode.isArray()) {
                // Limit to 40 jobs for demo purposes
                int limit = Math.min(jobsNode.size(), 40);
                for (int i = 0; i < limit; i++) {
                    JsonNode jobNode = jobsNode.get(i);
                    String jobTitle = jobNode.path("title").asText("Untitled Job");
                    progressService.sendProgress("PROCESSING", jobTitle, "Checking job details...");
                    
                    try {
                        Thread.sleep(500); 
                        saveOrUpdateJob(jobNode);
                    } catch (Exception e) {
                        progressService.sendProgress("ERROR", jobTitle, e.getMessage());
                    }
                }
            } else {
                 progressService.sendProgress("SKIPPED", "No Results", "API returned no data.");
            }
        } catch (Exception e) {
            progressService.sendProgress("ERROR", "API Call Failed", e.getMessage());
        }
        progressService.sendProgress("FINISHED", "Import Complete", "All jobs have been processed.");
    }

    private boolean saveOrUpdateJob(JsonNode jobNode) {
        String jobId = jobNode.path("id").asText();
        String title = jobNode.path("title").asText("Untitled Job");

        if (jobId == null || jobId.isEmpty() || jobId.equals("null")) {
            progressService.sendProgress("SKIPPED", title, "Job has no ID.");
            return false;
        }

        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(jobId);
        if (existingJobOpt.isPresent()) {
            progressService.sendProgress("SKIPPED", title, "Job already exists.");
            return false;
        }

        String companyName = jobNode.path("company_name").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        String url = jobNode.path("url").asText("");
        String location = jobNode.path("candidate_required_location").asText("");
        if (location.isEmpty() || location.equals("null")) location = "Worldwide";
        
        String category = jobNode.path("category").asText("IT/Software");
        String description = jobNode.path("description").asText("No description available.");

        Job job = new Job(jobId, title, location, "Unknown", url, category, description, company);

        if (jobNode.has("publication_date") && !jobNode.get("publication_date").isNull()) {
             try {
                String dateStr = jobNode.path("publication_date").asText();
                if(dateStr.length() >= 19) {
                     job.setCreatedAt(LocalDateTime.parse(dateStr.substring(0, 19)));
                }
             } catch (Exception e) {
                 // ignore
             }
        }

        job.setCompanyName(company.getName());
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(LocalDateTime.now());
        }

        Job savedJob = jobRepository.save(job);
        nerExtractionService.processJob(savedJob);
        
        Job finalJob = jobRepository.findById(savedJob.getId()).orElse(savedJob);
        String skills = finalJob.getSkills().stream().map(s -> s.getName()).collect(Collectors.joining(", "));
        progressService.sendProgress("SAVED", title, "Skills: " + (skills.isEmpty() ? "None found" : skills));

        return true;
    }
}