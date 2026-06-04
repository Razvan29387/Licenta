package com.example.demo.Controller;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.CompanyRepository;
import com.example.demo.Repository.JobRepository;
import com.example.demo.Service.EntityResolutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final EntityResolutionService entityResolutionService;

    public CompanyController(CompanyRepository companyRepository, JobRepository jobRepository, EntityResolutionService entityResolutionService) {
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
        this.entityResolutionService = entityResolutionService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Company> getCompanyById(@PathVariable Long id) {
        Optional<Company> company = companyRepository.findById(id);
        return company.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<Company> searchCompanyByName(@RequestParam String name) {
        Company company = entityResolutionService.findOrCreateCompany(name);
        return ResponseEntity.ok(company);
    }

    @PostMapping("/find-or-create")
    public ResponseEntity<Map<String, Object>> findOrCreateCompany(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        Company company = entityResolutionService.findOrCreateCompany(name);
        
        // Manually create a Map to ensure the ID is included in the JSON response
        Map<String, Object> response = new HashMap<>();
        response.put("id", company.getId());
        response.put("name", company.getName());
                
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/jobs")
    public ResponseEntity<List<Job>> getJobsByCompany(@PathVariable Long id) {
        List<Job> jobs = jobRepository.findByCompanyId(id);
        return ResponseEntity.ok(jobs);
    }
}