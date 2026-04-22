package com.example.demo.Controller;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.ApplicationRepository;
import com.example.demo.Repository.CompanyRepository;
import com.example.demo.Repository.JobRepository;
import com.example.demo.Request_DTO.JobRequestDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    public JobController(JobRepository jobRepository, CompanyRepository companyRepository, ApplicationRepository applicationRepository, Neo4jClient neo4jClient) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.neo4jClient = neo4jClient;
    }

    @GetMapping("/jobs")
    public Page<Job> getAllJobs(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        if (country != null && !country.isEmpty()) {
            return jobRepository.findByCountry(country, pageable);
        }
        if (category != null && !category.isEmpty()) {
            return jobRepository.findByCategory(category, pageable);
        }
        return jobRepository.findAll(pageable);
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
        newJob.setCompany(company);

        Job savedJob = jobRepository.save(newJob);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedJob);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalJobs = jobRepository.countJobs();
        long totalCompanies = companyRepository.countCompanies();
        long totalApplications = applicationRepository.count();

        // Query for jobs by country
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
        
        // Query for jobs by category
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

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalJobs", totalJobs);
        stats.put("totalCompanies", totalCompanies);
        stats.put("totalApplications", totalApplications);
        stats.put("jobsByCountry", jobsByCountry);
        stats.put("jobsByCategory", jobsByCategory);
        
        return ResponseEntity.ok(stats);
    }
}