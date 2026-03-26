package com.example.demo.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiAgentService {

    // IMPORTANT: Inlocuieste cu cheia ta API reala de la Google Gemini
    private final String GEMINI_API_KEY = "AIzaSyDrYcwUiBWHiFVObX72QuO5WvYcCSYtyr8";
    
    // URL-ul pentru modelul Gemini 1.5 Flash (bun pentru text si JSON)
    private final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiAgentService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Agent 1: Job Description Optimizer
     */
    public String optimizeJobDescription(String title, String rawNotes, String category) {
        String systemInstruction = "You are an expert IT HR Recruiter. Your task is to take brief notes provided by a company representative and transform them into a highly professional, attractive, and well-structured job description. Include standard sections like 'About the Role', 'Key Responsibilities', 'Requirements', and 'Benefits'. Use markdown bullet points for readability. Be concise but engaging.";
        String userPrompt = String.format("Job Title: %s\nCategory: %s\nRaw Notes/Keywords: %s\n\nPlease generate the full job description.", title, category, rawNotes);

        return callGemini(systemInstruction, userPrompt, false);
    }

    /**
     * Agent 2: Candidate Matchmaker (CV Screener)
     */
    public String evaluateCandidateCV(String jobDescription, String candidateCv) {
        String systemInstruction = "You are an expert HR AI Assistant. Your job is to evaluate a candidate's CV against a specific Job Description. You must return ONLY a JSON object with two fields: 'score' (an integer between 0 and 100 representing the match percentage) and 'feedback' (a short 2-sentence explanation of why you gave this score, highlighting pros and cons). Do not wrap the JSON in markdown code blocks like ```json, just return the raw JSON string.";
        String userPrompt = String.format("Job Description:\n%s\n\nCandidate CV:\n%s", jobDescription, candidateCv);

        // Fortam raspuns JSON
        return callGemini(systemInstruction, userPrompt, true);
    }

    private String callGemini(String systemInstruction, String userPrompt, boolean requireJson) {
        if(GEMINI_API_KEY.contains("placeholder")) {
            if(requireJson) {
                return "{\"score\": 88, \"feedback\": \"Simulated Gemini evaluation: Excellent match on primary skills, but lacks the required cloud experience.\"}";
            } else {
                return "This is a simulated **Gemini** AI response because the API key is missing.\n\n" +
                       "**About the Role**\nWe are looking for a fantastic candidate...\n\n" +
                       "**Responsibilities**\n- Build scalable systems\n- Collaborate with teams\n\n" +
                       "**Requirements**\n- 3+ years experience\n- Positive attitude";
            }
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Gemini API Body Structure
            Map<String, Object> requestBody = new HashMap<>();
            
            // 1. System Instruction (equivalent to "system" role in OpenAI)
            Map<String, Object> systemInstructionPart = new HashMap<>();
            systemInstructionPart.put("parts", List.of(Map.of("text", systemInstruction)));
            requestBody.put("system_instruction", systemInstructionPart);

            // 2. Contents (equivalent to "user" role in OpenAI)
            Map<String, Object> contentPart = new HashMap<>();
            contentPart.put("role", "user");
            contentPart.put("parts", List.of(Map.of("text", userPrompt)));
            requestBody.put("contents", List.of(contentPart));

            // 3. Generation Config (for forcing JSON if needed)
            if (requireJson) {
                Map<String, Object> generationConfig = new HashMap<>();
                generationConfig.put("response_mime_type", "application/json");
                requestBody.put("generationConfig", generationConfig);
            }

            // Aici adaugam "?key=" inainte de cheia API in mod dinamic!
            String fullUrl = GEMINI_API_URL + "?key=" + GEMINI_API_KEY;
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(fullUrl, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            
            // Extract text from Gemini's response structure
            // response.candidates[0].content.parts[0].text
            return root.path("candidates").get(0)
                       .path("content")
                       .path("parts").get(0)
                       .path("text").asText();

        } catch (Exception e) {
            e.printStackTrace();
            if (requireJson) {
                return "{\"score\": 0, \"feedback\": \"Error connecting to Gemini API.\"}";
            }
            return "Error generating description: " + e.getMessage();
        }
    }
}