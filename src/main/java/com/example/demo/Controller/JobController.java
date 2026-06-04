package com.example.demo.Controller;

import com.example.demo.Entity.Application;
import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.ApplicationRepository;
import com.example.demo.Repository.CompanyRepository;
import com.example.demo.Repository.JobRepository;
import com.example.demo.Request_DTO.JobRequestDTO;
import com.example.demo.Service.BatchJobUpdateService;
import com.example.demo.Service.EntityResolutionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final CompanyRepository companyRepository;
    private final Neo4jClient neo4jClient;
    private final BatchJobUpdateService batchJobUpdateService;
    private final EntityResolutionService entityResolutionService;

    public JobController(JobRepository jobRepository,
                         ApplicationRepository applicationRepository,
                         CompanyRepository companyRepository,
                         Neo4jClient neo4jClient,
                         BatchJobUpdateService batchJobUpdateService,
                         EntityResolutionService entityResolutionService) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.companyRepository = companyRepository;
        this.neo4jClient = neo4jClient;
        this.batchJobUpdateService = batchJobUpdateService;
        this.entityResolutionService = entityResolutionService;
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
        Company company = entityResolutionService.findOrCreateCompany(jobRequest.getCompanyName());

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
        newJob.setCompanyName(company.getName());

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

        Company company = entityResolutionService.findOrCreateCompany(jobRequest.getCompanyName());
        job.setCompany(company);
        job.setCompanyName(company.getName());

        Job updatedJob = jobRepository.save(job);
        return ResponseEntity.ok(updatedJob);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalJobs = jobRepository.countJobs();
        long totalCompanies = companyRepository.countCompanies();
        long totalApplications = applicationRepository.count();

        final String COUNT_KEY = "count";

        String countryQuery = "MATCH (j:Job) WHERE j.country IS NOT NULL AND j.country <> 'Unknown Country' AND j.country <> 'null' AND j.country <> 'Unknown' " +
                "RETURN j.country AS country, count(j) AS count ORDER BY count DESC LIMIT 10";

        List<Map<String, Object>> jobsByCountry = neo4jClient.query(countryQuery)
                .fetch()
                .all()
                .stream()
                .map(row -> Map.of("country", row.get("country"), COUNT_KEY, row.get("count")))
                .collect(Collectors.toList());

        String categoryQuery = "MATCH (j:Job) WHERE j.category IS NOT NULL AND j.category <> 'Uncategorized' AND j.category <> 'null' AND j.category <> 'Unknown' " +
                "RETURN j.category AS category, count(j) AS count ORDER BY count DESC LIMIT 10";

        List<Map<String, Object>> jobsByCategory = neo4jClient.query(categoryQuery)
                .fetch()
                .all()
                .stream()
                .map(row -> Map.of("category", row.get("category"), COUNT_KEY, row.get("count")))
                .collect(Collectors.toList());

        String languageQuery = "MATCH (j:Job) WHERE j.category = 'IT' AND j.programmingLanguages IS NOT NULL " +
                "UNWIND j.programmingLanguages AS language " +
                "RETURN language, count(j) AS count ORDER BY count DESC LIMIT 10";

        List<Map<String, Object>> jobsByLanguage = neo4jClient.query(languageQuery)
                .fetch()
                .all()
                .stream()
                .map(row -> Map.of("language", row.get("language"), COUNT_KEY, row.get("count")))
                .collect(Collectors.toList());

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