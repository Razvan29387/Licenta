package com.example.demo.Controller;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.CompanyRepository;
import com.example.demo.Repository.JobRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;

    public CompanyController(CompanyRepository companyRepository, JobRepository jobRepository) {
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Company> getCompanyById(@PathVariable Long id) {
        Optional<Company> company = companyRepository.findById(id);
        return company.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<Company> searchCompanyByName(@RequestParam String name) {
        Optional<Company> company = companyRepository.findByName(name.toUpperCase());
        return company.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/find-or-create")
    public ResponseEntity<Company> findOrCreateCompany(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        String normalizedName = name.trim().toUpperCase();
        Company company = companyRepository.findByName(normalizedName)
                .orElseGet(() -> companyRepository.save(new Company(normalizedName)));
                
        return ResponseEntity.ok(company);
    }

    @GetMapping("/{id}/jobs")
    public ResponseEntity<List<Job>> getJobsByCompany(@PathVariable Long id) {
        List<Job> jobs = jobRepository.findByCompanyId(id);
        return ResponseEntity.ok(jobs);
    }
}