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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AdzunaIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AdzunaIngestionService.class);

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final BatchJobUpdateService batchJobUpdateService;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;

    @Value("${adzuna.api.id}")
    private String APP_ID;

    @Value("${adzuna.api.key}")
    private String APP_KEY;

    private final String BASE_URL = "https://api.adzuna.com/v1/api/jobs";

    public AdzunaIngestionService(JobRepository jobRepository, CompanyRepository companyRepository, BatchJobUpdateService batchJobUpdateService, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.batchJobUpdateService = batchJobUpdateService;
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
    }

    @Async("taskExecutor")
    public void importJobs(String country, int startPage, int endPage) {
        log.info("Adzuna ({}) - Starting IT-only import from page {} to {}", country.toUpperCase(), startPage, endPage);

        for (int page = startPage; page <= endPage; page++) {
            try {
                String url = String.format("%s/%s/search/%d?app_id=%s&app_key=%s&results_per_page=50&content-type=application/json&category=it-jobs",
                        BASE_URL, country, page, APP_ID, APP_KEY);

                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(jsonResponse);
                JsonNode results = root.path("results");

                if (results.isArray()) {
                    for (JsonNode jobNode : results) {
                        saveOrUpdateJob(jobNode, country);
                    }
                }

                Thread.sleep(1000);

            } catch (Exception e) {
                log.error("Adzuna ({}) - Error importing page {}: {}", country.toUpperCase(), page, e.getMessage());
            }
        }
        log.info("Adzuna ({}) - Import finished.", country.toUpperCase());

        // Apelează automat batch update-ul pentru a actualiza toți joburile cu remote status și contract type
        log.info("Adzuna ({}) - Starting automatic batch update for remote status and contract type...", country.toUpperCase());
        batchJobUpdateService.updateAllJobsWithRemoteAndContractType();
    }

    private void saveOrUpdateJob(JsonNode jobNode, String countryCode) {
        String adzunaId = jobNode.path("id").asText();

        // 1. Găsim compania folosind EntityResolutionService
        String companyName = jobNode.path("company").path("display_name").asText("Unknown Company").trim();
        Company company = entityResolutionService.findOrCreateCompany(companyName);

        // 2. Verificăm dacă jobul există deja
        Optional<Job> existingJobOpt = jobRepository.findByAdzunaId(adzunaId);
        Job job;

        if (existingJobOpt.isPresent()) {
            // Dacă există, îl folosim pe cel din DB pentru a-i face update
            job = existingJobOpt.get();
            job.setCompany(company); // Ne asigurăm că are o companie asociată
        } else {
            // Dacă nu există, instanțiem unul nou cu detaliile de bază
            String title = jobNode.path("title").asText();
            String url = jobNode.path("redirect_url").asText();
            String location = jobNode.path("location").path("display_name").asText("Unknown Location");
            String country = countryCode.toUpperCase();
            String category = jobNode.path("category").path("label").asText("Uncategorized");
            String description = jobNode.path("description").asText("No description available.");

            job = new Job(adzunaId, title, location, country, url, category, description, company);
        }

        // --- 3. ACTUALIZĂM TOATE CÂMPURILE (Aplicabil și pentru Job Nou și pentru Update) ---

        // Suprascriem descrierea și titlul în caz că angajatorul le-a modificat
        if (jobNode.has("description") && !jobNode.get("description").isNull()) {
            job.setDescription(jobNode.path("description").asText());
        }
        if (jobNode.has("title") && !jobNode.get("title").isNull()) {
            job.setTitle(jobNode.path("title").asText());
        }

        // Salarii
        if (jobNode.has("salary_min") && !jobNode.get("salary_min").isNull()) {
            job.setSalaryMin(jobNode.path("salary_min").asDouble());
        }
        if (jobNode.has("salary_max") && !jobNode.get("salary_max").isNull()) {
            job.setSalaryMax(jobNode.path("salary_max").asDouble());
        }
        if (job.getSalaryMin() != null || job.getSalaryMax() != null) {
            job.setSalaryPeriod("year");
        }

        // Predicție Salariu
        if (jobNode.has("salary_is_predicted") && !jobNode.get("salary_is_predicted").isNull()) {
            job.setSalaryIsPredicted(jobNode.path("salary_is_predicted").asInt() == 1);
        }

        // Coordonate GPS
        if (jobNode.has("latitude") && !jobNode.get("latitude").isNull()) {
            job.setLatitude(jobNode.path("latitude").asDouble());
        }
        if (jobNode.has("longitude") && !jobNode.get("longitude").isNull()) {
            job.setLongitude(jobNode.path("longitude").asDouble());
        }

        // Detalii Locație (Array)
        JsonNode locationAreaNode = jobNode.path("location").path("area");
        if (locationAreaNode.isArray()) {
            List<String> areas = new ArrayList<>();
            for (JsonNode area : locationAreaNode) {
                areas.add(area.asText());
            }
            job.setLocationArea(areas);
        }

        // Detalii Categorie (Tag)
        if (jobNode.path("category").has("tag")) {
            job.setCategoryTag(jobNode.path("category").path("tag").asText());
        }

        // Tip Contract
        if (jobNode.has("contract_type") && !jobNode.get("contract_type").isNull()) {
            job.setContractType(jobNode.path("contract_type").asText());
        }

        // Remote status - verific în description sau din response
        boolean isRemote = false;
        if (jobNode.has("description") && !jobNode.get("description").isNull()) {
            String desc = jobNode.path("description").asText().toLowerCase();
            isRemote = desc.contains("remote") || desc.contains("work from home") || desc.contains("wfh");
        }
        job.setJobIsRemote(isRemote);

        // Data Creării
        if (jobNode.has("created") && !jobNode.get("created").isNull()) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(jobNode.path("created").asText());
                job.setCreatedAt(odt.toLocalDateTime());
            } catch (Exception e) {
                log.warn("Could not parse created date for job {}: {}", adzunaId, e.getMessage());
            }
        }

        job.setCompanyName(company.getName()); // Use the resolved name

        // Ensure createdAt is set to the current time (for new jobs or updates)
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(java.time.LocalDateTime.now());
        }

        // 4. Salvare în baza de date (dacă e job vechi va face UPDATE, dacă e nou va face INSERT)
        Job savedJob = jobRepository.save(job);
        
        // 5. Rulăm extragerea NER
        nerExtractionService.processJob(savedJob);
    }
}