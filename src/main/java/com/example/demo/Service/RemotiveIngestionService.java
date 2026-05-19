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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RemotiveIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RemotiveIngestionService.class);

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // We focus on the Software Development category for relevance
    private final String BASE_URL = "https://remotive.com/api/remote-jobs?category=software-dev";

    public RemotiveIngestionService(JobRepository jobRepository, CompanyRepository companyRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Async("taskExecutor")
    public void importJobs() {
        log.info("Remotive - Starting import for IT/Software remote jobs");

        try {
            // Remotive returns all jobs for a category in a single, large response, not paginated.
            String jsonResponse = restTemplate.getForObject(BASE_URL, String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode jobsArray = root.path("jobs");

            if (jobsArray.isArray()) {
                int count = 0;
                log.info("Remotive - Found {} jobs in the response. Starting processing...", jobsArray.size());
                
                for (JsonNode jobNode : jobsArray) {
                    saveOrUpdateJob(jobNode);
                    count++;
                    
                    // Small sleep to not overwhelm the database with thousands of rapid writes
                    if (count % 50 == 0) {
                        Thread.sleep(500); 
                    }
                }
                log.info("Remotive - Successfully processed {} jobs.", count);
            } else {
                 log.warn("Remotive - 'jobs' element in response is not an array.");
            }

        } catch (Exception e) {
            log.error("Remotive - Error importing jobs: {}", e.getMessage(), e);
        }
        log.info("Remotive - Import finished.");
    }

    private void saveOrUpdateJob(JsonNode jobNode) {
        String remotiveId = "remotive_" + jobNode.path("id").asText();
        
        if (remotiveId.equals("remotive_null") || remotiveId.isEmpty()) {
            return;
        }

        // --- Extragere Companie ---
        String companyName = jobNode.path("company_name").asText("Unknown Company").trim();
        Company company = findOrCreateCompany(companyName);
        
        // Dacă API-ul oferă logo, îl putem adăuga la companie pe viitor

        // --- Verificare Job Existent ---
        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(remotiveId);
        Job job;

        if (existingJobOpt.isPresent()) {
            job = existingJobOpt.get();
            job.setCompany(company); // Asigurăm relația
        } else {
            // Instanțiere Job Nou
            String title = jobNode.path("title").asText("Untitled Job");
            String url = jobNode.path("url").asText("");
            
            // Remotive jobs are remote, but they might specify allowed regions (e.g. "Worldwide", "USA Only")
            String candidateRequiredLocation = jobNode.path("candidate_required_location").asText("");
            String country = "Remote"; 
            String location = candidateRequiredLocation.isEmpty() ? "Worldwide" : candidateRequiredLocation;
            
            String category = jobNode.path("category").asText("IT/Software");
            String description = "No description available.";

            job = new Job(remotiveId, title, location, country, url, category, description, company);
            job.setJobIsRemote(true); // By definition, Remotive jobs are remote
        }

        // --- ACTUALIZARE CÂMPURI ---

        // Descriere Completă (Remotive dă un HTML lung și frumos)
        if (jobNode.has("description") && !jobNode.get("description").isNull()) {
            String rawDesc = jobNode.path("description").asText();
            // Aici nu mai ștergem HTML-ul complet ca la Arbeitnow, pentru că s-ar putea 
            // ca acel coleg să aibă nevoie de structură (ex: <ul><li>) pentru analiză mai bună.
            // Doar tăiem spațiile goale.
            job.setDescription(rawDesc.trim());
        }
        
        if (jobNode.has("title") && !jobNode.get("title").isNull()) {
            job.setTitle(jobNode.path("title").asText());
        }

        // Tip Contract
        if (jobNode.has("job_type") && !jobNode.get("job_type").isNull()) {
            // Remotive usually returns strings like "full_time", "contract", etc.
            job.setContractType(jobNode.path("job_type").asText());
        }
        
        // Tag-uri
        JsonNode tagsNode = jobNode.path("tags");
        if (tagsNode.isArray() && tagsNode.size() > 0) {
            List<String> tags = new ArrayList<>();
            for (JsonNode tag : tagsNode) {
                tags.add(tag.asText());
            }
            // Putem salva tag-urile ca programmingLanguages temporar pentru a fi extrase de coleg
            job.setProgrammingLanguages(tags);
        }

        // Data Creării (Format: 2023-11-21T10:45:00)
        if (jobNode.has("publication_date") && !jobNode.get("publication_date").isNull()) {
            try {
                String pubDateStr = jobNode.path("publication_date").asText();
                if (pubDateStr.length() >= 19) {
                    LocalDateTime ldt = LocalDateTime.parse(pubDateStr.substring(0, 19));
                    job.setCreatedDate(ldt);
                }
            } catch (Exception e) {
                log.warn("Remotive - Could not parse publication date: {}", e.getMessage());
            }
        }

        job.setCompanyName(companyName);

        if (job.getCreatedAt() == null) {
            job.setCreatedAt(LocalDateTime.now());
        }

        jobRepository.save(job);
    }

    @Transactional
    protected Company findOrCreateCompany(String name) {
        return companyRepository.findByName(name)
                .orElseGet(() -> companyRepository.save(new Company(name)));
    }
}