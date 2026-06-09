package com.example.demo.Controller;

import com.example.demo.Request_DTO.OptimizeDescriptionRequest;
import com.example.demo.Service.GeminiAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AIAgentController {

    private final GeminiAgentService geminiAgentService;

    public AIAgentController(GeminiAgentService geminiAgentService) {
        this.geminiAgentService = geminiAgentService;
    }

    @PostMapping("/optimize-description")
    public ResponseEntity<String> optimizeDescription(@RequestBody OptimizeDescriptionRequest request) {
        if (request.getTitle() == null || request.getRawNotes() == null || request.getCategory() == null) {
            return ResponseEntity.badRequest().body("Title, rawNotes, and category are required.");
        }

        String optimizedText = geminiAgentService.optimizeJobDescription(
                request.getTitle(),
                request.getRawNotes(),
                request.getCategory()
        );

        return ResponseEntity.ok(optimizedText);
    }
}
