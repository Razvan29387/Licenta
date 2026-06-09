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
import java.util.List;
import java.util.Map;

@Service
public class GeminiAgentService {

    @Value("${gemini.api.key}")
    private String GEMINI_API_KEY;
    
    private final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent"; // Updated to latest model

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiAgentService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public String ask(String userPrompt) {
        String systemInstruction = "You are a helpful assistant. Respond concisely and accurately.";
        return callGemini(systemInstruction, userPrompt, false);
    }

    public String optimizeJobDescription(String title, String rawNotes, String category) {
        String systemInstruction = "You are an expert IT HR Recruiter. Your task is to take brief notes provided by a company representative and transform them into a highly professional, attractive, and well-structured job description. Write the entire description using clear, narrative paragraphs. Do NOT use any symbols, bullet points, asterisks (*), hashtags (#), or special characters. Use ONLY standard letters, numbers, and standard punctuation (periods, commas). Include topics like About the Role, Key Responsibilities, Requirements, and Benefits, but integrate them naturally into the paragraphs rather than using lists or distinct headings with symbols. Be concise but engaging.";
        String userPrompt = String.format("Job Title: %s\nCategory: %s\nRaw Notes/Keywords: %s\n\nPlease generate the full job description according to the strict formatting rules.", title, category, rawNotes);

        return callGemini(systemInstruction, userPrompt, false);
    }

    public String evaluateCandidateCV(String jobDescription, String candidateCv) {
        String systemInstruction = "You are an expert HR AI Assistant. Your job is to evaluate a candidate's CV against a specific Job Description. You must return ONLY a JSON object with two fields: 'score' (an integer between 0 and 100 representing the match percentage) and 'feedback' (a short 2-sentence explanation of why you gave this score, highlighting pros and cons). Do not wrap the JSON in markdown code blocks like ```json, just return the raw JSON string.";
        String userPrompt = String.format("Job Description:\n%s\n\nCandidate CV:\n%s", jobDescription, candidateCv);

        return callGemini(systemInstruction, userPrompt, true);
    }

    private String callGemini(String systemInstruction, String userPrompt, boolean requireJson) {
        if(GEMINI_API_KEY == null || GEMINI_API_KEY.isEmpty() || GEMINI_API_KEY.contains("placeholder")) {
            if(requireJson) {
                return "{\"score\": 88, \"feedback\": \"Simulated Gemini evaluation: Excellent match on primary skills, but lacks the required cloud experience.\"}";
            } else {
                return "This is a simulated Gemini AI response because the API key is missing. We are looking for a fantastic candidate to build scalable systems. Responsibilities include collaborating with teams. Requirements are 3 years of experience and a positive attitude.";
            }
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            
            Map<String, Object> systemInstructionPart = new HashMap<>();
            systemInstructionPart.put("parts", List.of(Map.of("text", systemInstruction)));
            requestBody.put("system_instruction", systemInstructionPart);

            Map<String, Object> contentPart = new HashMap<>();
            contentPart.put("role", "user");
            contentPart.put("parts", List.of(Map.of("text", userPrompt)));
            requestBody.put("contents", List.of(contentPart));

            if (requireJson) {
                Map<String, Object> generationConfig = new HashMap<>();
                generationConfig.put("response_mime_type", "application/json");
                requestBody.put("generationConfig", generationConfig);
            }

            String fullUrl = GEMINI_API_URL + "?key=" + GEMINI_API_KEY;
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(fullUrl, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            
            return root.path("candidates").get(0)
                       .path("content")
                       .path("parts").get(0)
                       .path("text").asText();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Gemini API Error: " + e.getMessage());
            if (requireJson) {
                return "{\"score\": 0, \"feedback\": \"Error connecting to Gemini API.\"}";
            }
            return "Error generating response: " + e.getMessage();
        }
    }
}
