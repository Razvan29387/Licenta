package com.example.demo.Controller;

import com.example.demo.Entity.Application;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.ApplicationRepository;
import com.example.demo.Repository.JobRepository;
import com.example.demo.Request_DTO.SubmitApplicationRequest;
import com.example.demo.Service.GeminiAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final GeminiAgentService aiAgentService;
    private final ObjectMapper objectMapper;

    public ApplicationController(ApplicationRepository applicationRepository, JobRepository jobRepository, GeminiAgentService aiAgentService) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.aiAgentService = aiAgentService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/job/{jobId}")
    public ResponseEntity<String> submitApplication(@PathVariable Long jobId, @RequestBody SubmitApplicationRequest request) {
        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }

        Job job = jobOpt.get();
        
        Application newApp = new Application(request.getApplicantName(), request.getCandidateCv(), job);
        
        try {
            String evaluationJson = aiAgentService.evaluateCandidateCV(job.getTitle() + "\n" + job.getDescription(), request.getCandidateCv());
            JsonNode root = objectMapper.readTree(evaluationJson);
            if (root.has("score")) newApp.setAiScore(root.path("score").asInt());
            if (root.has("feedback")) newApp.setAiFeedback(root.path("feedback").asText());
        } catch (Exception e) {
            System.err.println("Failed to parse AI evaluation: " + e.getMessage());
            newApp.setAiScore(0);
            newApp.setAiFeedback("AI Evaluation failed.");
        }

        applicationRepository.save(newApp);

        return ResponseEntity.status(HttpStatus.CREATED).body("Application submitted successfully!");
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<List<Application>> getJobApplications(@PathVariable Long jobId) {
        List<Application> applications = applicationRepository.findByJobIdOrderByAiScoreDesc(jobId);
        return ResponseEntity.ok(applications);
    }
}