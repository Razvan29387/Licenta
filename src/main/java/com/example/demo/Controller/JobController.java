package com.example.demo.Controller;

import com.example.demo.Entity.Application;
import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.ApplicationRepository;
import com.example.demo.Repository.CompanyRepository;
import com.example.demo.Repository.JobRepository;
import com.example.demo.Request_DTO.JobRequestDTO;
import com.example.demo.Service.BatchJobUpdateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.neo4j.core.Neo4jClient;


import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final Neo4jClient neo4jClient;
    private final BatchJobUpdateService batchJobUpdateService;

    public JobController(JobRepository jobRepository, CompanyRepository companyRepository,
                         ApplicationRepository applicationRepository, Neo4jClient neo4jClient,
                         BatchJobUpdateService batchJobUpdateService) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.neo4jClient = neo4jClient;
        this.batchJobUpdateService = batchJobUpdateService;
    }

    @GetMapping("/jobs")
    public Page<Job> getAllJobs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        if (search != null && !search.isEmpty()) {
            return jobRepository.searchJobsByKeyword(search, pageable);
        }
        if (country != null && !country.isEmpty()) {
            return jobRepository.findByCountry(country, pageable);
        }
        if (category != null && !category.isEmpty()) {
            return jobRepository.findByCategory(category, pageable);
        }
        return jobRepository.findAll(pageable);
    }

    @GetMapping("/export/jobs")
    public List<Job> exportAllJobs() {
        return jobRepository.findAll();
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getJobById(@PathVariable Long id) {
        Optional<Job> job = jobRepository.findById(id);
        return job.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs/{id}/apply")
    public ResponseEntity<Void> applyToJob(@PathVariable Long id) {
        Optional<Job> job = jobRepository.findById(id);
        if (job.isPresent() && job.get().getUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(job.get().getUrl()))
                    .build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/jobs/{id}/applications")
    public ResponseEntity<List<Application>> getJobApplications(@PathVariable Long id) {
        List<Application> applications = applicationRepository.findByJobIdOrderByAiScoreDesc(id);
        return ResponseEntity.ok(applications);
    }

    @PostMapping("/jobs")
    public ResponseEntity<Job> createJob(@RequestBody JobRequestDTO jobRequest) {
        Company company = companyRepository.findByName(jobRequest.getCompanyName())
                .orElseGet(() -> {
                    Company newCompany = new Company(jobRequest.getCompanyName());
                    return companyRepository.save(newCompany);
                });

        String generatedId = "local_" + UUID.randomUUID().toString();

        Job newJob = new Job(
                generatedId,
                jobRequest.getTitle(),
                jobRequest.getLocation(),
                jobRequest.getCountry(),
                null,
                jobRequest.getCategory(),
                jobRequest.getDescription(),
                company
        );

        newJob.setExperienceLevel(jobRequest.getExperienceLevel());
        newJob.setProgrammingLanguages(jobRequest.getProgrammingLanguages());
        newJob.setCompanyName(jobRequest.getCompanyName());

        Job savedJob = jobRepository.save(newJob);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedJob);
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<Job> updateJob(@PathVariable Long id, @RequestBody JobRequestDTO jobRequest) {
        Optional<Job> jobOptional = jobRepository.findById(id);
        if (jobOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Job job = jobOptional.get();
        job.setTitle(jobRequest.getTitle());
        job.setDescription(jobRequest.getDescription());
        job.setLocation(jobRequest.getLocation());
        job.setCountry(jobRequest.getCountry());
        job.setCategory(jobRequest.getCategory());
        job.setExperienceLevel(jobRequest.getExperienceLevel());
        job.setProgrammingLanguages(jobRequest.getProgrammingLanguages());

        Job updatedJob = jobRepository.save(job);
        return ResponseEntity.ok(updatedJob);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalJobs = jobRepository.countJobs();
        long totalCompanies = companyRepository.countCompanies();
        long totalApplications = applicationRepository.count();

        String countryQuery = "MATCH (j:Job) WHERE j.country IS NOT NULL AND j.country <> 'Unknown Country' AND j.country <> 'null' AND j.country <> 'Unknown' " +
                "RETURN j.country AS country, count(j) AS count ORDER BY count DESC LIMIT 10";

        Collection<Map> countryData = neo4jClient.query(countryQuery)
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> Map.of("country", record.get("country").asString(), "count", record.get("count").asLong()))
                .all();

        List<Map<String, Object>> jobsByCountry = new ArrayList<>();
        for (Map m : countryData) {
            jobsByCountry.add((Map<String, Object>) m);
        }

        String categoryQuery = "MATCH (j:Job) WHERE j.category IS NOT NULL AND j.category <> 'Uncategorized' AND j.category <> 'null' AND j.category <> 'Unknown' " +
                "RETURN j.category AS category, count(j) AS count ORDER BY count DESC LIMIT 10";

        Collection<Map> categoryData = neo4jClient.query(categoryQuery)
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> Map.of("category", record.get("category").asString(), "count", record.get("count").asLong()))
                .all();

        List<Map<String, Object>> jobsByCategory = new ArrayList<>();
        for (Map m : categoryData) {
            jobsByCategory.add((Map<String, Object>) m);
        }

        String languageQuery = "MATCH (j:Job) WHERE j.category = 'IT' AND j.programmingLanguages IS NOT NULL " +
                "UNWIND j.programmingLanguages AS language " +
                "RETURN language, count(j) AS count ORDER BY count DESC LIMIT 10";

        Collection<Map> languageData = neo4jClient.query(languageQuery)
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> Map.of("language", record.get("language").asString(), "count", record.get("count").asLong()))
                .all();

        List<Map<String, Object>> jobsByLanguage = new ArrayList<>();
        for (Map m : languageData) {
            jobsByLanguage.add((Map<String, Object>) m);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalJobs", totalJobs);
        stats.put("totalCompanies", totalCompanies);
        stats.put("totalApplications", totalApplications);
        stats.put("jobsByCountry", jobsByCountry);
        stats.put("jobsByCategory", jobsByCategory);
        stats.put("jobsByLanguage", jobsByLanguage);

        return ResponseEntity.ok(stats);
    }

    @PostMapping("/batch-update/all-jobs-remote-contract")
    public ResponseEntity<String> batchUpdateAllJobsRemoteAndContract() {
        batchJobUpdateService.updateAllJobsWithRemoteAndContractType();
        return ResponseEntity.ok("Batch update started. All jobs are being updated with remote status and contract type.");
    }
}