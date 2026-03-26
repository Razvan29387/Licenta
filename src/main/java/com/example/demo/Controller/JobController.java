package com.example.demo.Controller;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.CompanyRepository;
import com.example.demo.Repository.JobRepository;
import com.example.demo.Request_DTO.JobRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;

    public JobController(JobRepository jobRepository, CompanyRepository companyRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
    }

    @GetMapping("/jobs")
    public List<Job> getAllJobs(@RequestParam(required = false) String companyName) {
        if (companyName != null && !companyName.isEmpty()) {
            return jobRepository.findByCompanyName(companyName);
        }
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

    @PostMapping("/jobs")
    public ResponseEntity<Job> createJob(@RequestBody JobRequestDTO jobRequest) {
        // 1. Găsim compania existentă sau o creăm dacă nu există
        Company company = companyRepository.findByName(jobRequest.getCompanyName())
                .orElseGet(() -> {
                    Company newCompany = new Company(jobRequest.getCompanyName());
                    return companyRepository.save(newCompany);
                });

        // 2. Generăm un ID unic intern pentru jobul creat pe platformă
        String generatedId = "local_" + UUID.randomUUID().toString();
        
        // 3. Creăm entitatea Job
        Job newJob = new Job(
                generatedId,
                jobRequest.getTitle(),
                jobRequest.getLocation(),
                jobRequest.getCountry(),
                null, // Fără URL extern, se aplică pe platformă
                jobRequest.getCategory(),
                jobRequest.getDescription(),
                company
        );
        
        // 4. Setăm noile câmpuri
        newJob.setExperienceLevel(jobRequest.getExperienceLevel());
        newJob.setProgrammingLanguages(jobRequest.getProgrammingLanguages());
        newJob.setCompany(company);

        // 5. Salvăm în baza de date Memgraph
        Job savedJob = jobRepository.save(newJob);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedJob);
    }
}