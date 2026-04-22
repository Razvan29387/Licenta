package com.example.demo.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class OpenAIAgentService {

    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;
    private final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAIAgentService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Agent 1: Job Description Optimizer
     */
    public String optimizeJobDescription(String title, String rawNotes, String category) {
        String systemPrompt = "You are an expert IT HR Recruiter. Your task is to take brief notes provided by a company representative and transform them into a highly professional, attractive, and well-structured job description. " +
                "Include standard sections like 'About the Role', 'Key Responsibilities', 'Requirements', and 'Benefits'. Use markdown bullet points for readability. Be concise but engaging.";
        
        String userPrompt = String.format("Job Title: %s\nCategory: %s\nRaw Notes/Keywords: %s\n\nPlease generate the full job description.", title, category, rawNotes);

        return callOpenAI(systemPrompt, userPrompt);
    }

    /**
     * Agent 2: Candidate Matchmaker (CV Screener)
     */
    public String evaluateCandidateCV(String jobDescription, String candidateCv) {
        String systemPrompt = "You are an expert HR AI Assistant. Your job is to evaluate a candidate's CV against a specific Job Description. " +
                "You must return ONLY a JSON object with two fields: 'score' (an integer between 0 and 100 representing the match percentage) and 'feedback' (a short 2-sentence explanation of why you gave this score, highlighting pros and cons).";
        
        String userPrompt = String.format("Job Description:\n%s\n\nCandidate CV:\n%s", jobDescription, candidateCv);

        // We force the AI to return JSON
        return callOpenAIJSON(systemPrompt, userPrompt);
    }

    private String callOpenAI(String systemPrompt, String userPrompt) {
        if(OPENAI_API_KEY == null || OPENAI_API_KEY.isEmpty() || OPENAI_API_KEY.contains("placeholder")) {
            return "This is a simulated AI response because the OpenAI API key is missing.\n\n" +
                   "**About the Role**\nWe are looking for a great candidate...\n\n" +
                   "**Responsibilities**\n- Write code\n- Drink coffee\n\n" +
                   "**Requirements**\n- Experience with Java\n- Good communication skills";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(OPENAI_API_KEY);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("messages", new Object[]{
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            });

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_API_URL, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error generating description: " + e.getMessage();
        }
    }

    private String callOpenAIJSON(String systemPrompt, String userPrompt) {
         if(OPENAI_API_KEY == null || OPENAI_API_KEY.isEmpty() || OPENAI_API_KEY.contains("placeholder")) {
            return "{\"score\": 85, \"feedback\": \"Simulated evaluation: Strong match on technical skills, but lacks the required years of management experience.\"}";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(OPENAI_API_KEY);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("response_format", Map.of("type", "json_object")); // Force JSON output
            requestBody.put("messages", new Object[]{
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            });

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_API_URL, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"score\": 0, \"feedback\": \"Error connecting to AI service.\"}";
        }
    }
}