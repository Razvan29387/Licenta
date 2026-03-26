package com.example.demo.Controller;

import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import com.example.demo.Request_DTO.AIOptimizeRequest;
import com.example.demo.Request_DTO.CVEvaluationRequest;
import com.example.demo.Service.GeminiAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/ai")
public class AIAgentController {

    private final GeminiAgentService aiAgentService;
    private final JobRepository jobRepository;

    public AIAgentController(GeminiAgentService aiAgentService, JobRepository jobRepository) {
        this.aiAgentService = aiAgentService;
        this.jobRepository = jobRepository;
    }

    // 1. Endpoint for Company to optimize job descriptions
    @PostMapping("/optimize-description")
    public ResponseEntity<Map<String, String>> optimizeDescription(@RequestBody AIOptimizeRequest request) {
        String optimizedText = aiAgentService.optimizeJobDescription(
                request.getTitle(), 
                request.getRawNotes(), 
                request.getCategory()
        );
        return ResponseEntity.ok(Map.of("optimizedDescription", optimizedText));
    }

    // 2. Endpoint for Candidates to evaluate their CV against a specific Job
    @PostMapping("/evaluate-cv/{jobId}")
    public ResponseEntity<String> evaluateCV(@PathVariable Long jobId, @RequestBody CVEvaluationRequest request) {
        Optional<Job> jobOpt = jobRepository.findById(jobId);
        
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Job job = jobOpt.get();
        String jobDescription = job.getTitle() + "\n" + job.getDescription();
        
        // This will return a raw JSON string from Gemini (or the simulated one)
        String evaluationResultJson = aiAgentService.evaluateCandidateCV(jobDescription, request.getCandidateCv());
        
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(evaluationResultJson);
    }
}