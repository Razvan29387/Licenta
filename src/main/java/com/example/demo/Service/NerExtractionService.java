package com.example.demo.Service;

import com.example.demo.Entity.Job;
import com.example.demo.Entity.Skill;
import com.example.demo.Repository.JobRepository;
import com.example.demo.Repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class NerExtractionService {

    private static final Logger log = LoggerFactory.getLogger(NerExtractionService.class);

    private final SkillRepository skillRepository;
    private final JobRepository jobRepository;
    private final GeminiAgentService geminiAgentService;

    private static final Set<String> PROGRAMMING_LANGUAGES = new HashSet<>(Arrays.asList(
            "java", "python", "javascript", "typescript", "c++", "c#", "ruby", "go", "php", "swift", "kotlin", "scala", "rust", "sql"
    ));

    private static final Set<String> FRAMEWORKS = new HashSet<>(Arrays.asList(
            "react", "angular", "vue", "spring boot", "django", "flask", "express", "laravel", "rails", ".net", "node.js", "next.js"
    ));

    private static final Set<String> TECHNOLOGIES = new HashSet<>(Arrays.asList(
            "docker", "kubernetes", "aws", "azure", "gcp", "mysql", "postgresql", "mongodb", "redis", "elasticsearch", "git", "jenkins", "terraform", "linux"
    ));

    private static final Set<String> ALL_KNOWN_SKILLS = new HashSet<>();
    static {
        ALL_KNOWN_SKILLS.addAll(PROGRAMMING_LANGUAGES);
        ALL_KNOWN_SKILLS.addAll(FRAMEWORKS);
        ALL_KNOWN_SKILLS.addAll(TECHNOLOGIES);
    }

    public NerExtractionService(SkillRepository skillRepository, JobRepository jobRepository, GeminiAgentService geminiAgentService) {
        this.skillRepository = skillRepository;
        this.jobRepository = jobRepository;
        this.geminiAgentService = geminiAgentService;
    }

    @Async("taskExecutor")
    public void processJob(Job job) {
        if (job.getDescription() == null || job.getDescription().isEmpty()) {
            return;
        }

        // --- Step 1: Rule-based extraction (Gazetteer matching) ---
        Set<String> extractedKeywords = extractKeywords(job.getDescription());
        Set<Skill> linkedSkills = new HashSet<>();
        for (String keyword : extractedKeywords) {
            Skill skill = findOrCreateSkill(keyword);
            linkedSkills.add(skill);
        }
        log.info("Job ID {}: Extracted {} skills from dictionaries.", job.getId(), linkedSkills.size());

        // --- Step 2: LLM-based extraction for complex/soft skills ---
        try {
            Set<String> llmExtractedSkills = askLLMForComplexEntities(job.getDescription(), extractedKeywords);
            for (String skillName : llmExtractedSkills) {
                if (!skillName.isEmpty()) {
                    Skill skill = findOrCreateSkill(skillName); // Reuse the same linking logic
                    linkedSkills.add(skill);
                }
            }
            log.info("Job ID {}: Added {} skills from LLM analysis.", job.getId(), llmExtractedSkills.size());
        } catch (Exception e) {
            log.error("Job ID {}: Failed to process LLM extraction. Reason: {}", job.getId(), e.getMessage());
        }

        // --- Final Step: Update the job with all linked skills ---
        job.setSkills(linkedSkills);
        jobRepository.save(job);
        
        log.info("Finished NER processing for job ID {}. Total skills linked: {}", job.getId(), linkedSkills.size());
    }

    private Set<String> extractKeywords(String text) {
        Set<String> found = new HashSet<>();
        String lowerText = " " + text.toLowerCase().replaceAll("[^a-z0-9+#.\\-]", " ") + " ";

        for (String skill : ALL_KNOWN_SKILLS) {
            String pattern = ".*\\b" + Pattern.quote(skill) + "\\b.*";
            if (lowerText.matches(pattern)) {
                found.add(skill);
            }
        }
        return found;
    }

    private Skill findOrCreateSkill(String name) {
        String normalizedName = normalizeSkillName(name);

        Optional<Skill> exactMatch = skillRepository.findByName(normalizedName);
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        Iterable<Skill> allSkills = skillRepository.findAll();
        for (Skill existingSkill : allSkills) {
            if (calculateSimilarity(normalizedName, existingSkill.getName()) > 0.90) {
                log.debug("Linked extracted skill '{}' to existing node '{}'", normalizedName, existingSkill.getName());
                return existingSkill;
            }
        }

        Skill newSkill = new Skill(normalizedName);
        return skillRepository.save(newSkill);
    }

    private String normalizeSkillName(String name) {
        return name.toLowerCase().trim();
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        if (s1.length() == 0 || s2.length() == 0) return 0.0;
        
        int distance = computeLevenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());
        
        return 1.0 - ((double) distance / maxLength);
    }

    private int computeLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) dp[i][j] = j;
                else if (j == 0) dp[i][j] = i;
                else {
                    int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
                }
            }
        }
        return dp[s1.length()][s2.length()];
    }

    private Set<String> askLLMForComplexEntities(String text, Set<String> alreadyFound) {
        String existingSkills = String.join(", ", alreadyFound);
        String prompt = String.format(
            "Analyze the following job description. Your task is to identify technical skills, tools, or methodologies that are NOT in this list: [%s]. " +
            "Focus on specific, non-generic terms (e.g., 'JIRA', 'Confluence', 'Scrum', 'Agile', 'CI/CD', 'Figma'). " +
            "Do not extract soft skills like 'communication' or 'teamwork'. " +
            "Return ONLY a comma-separated list of the new skills you find. If you find no new skills, return an empty string. " +
            "Job Description:\n\n\"%s\"",
            existingSkills, text
        );

        String response = geminiAgentService.ask(prompt);
        if (response == null || response.trim().isEmpty()) {
            return new HashSet<>();
        }

        return Arrays.stream(response.split(","))
                     .map(String::trim)
                     .filter(s -> !s.isEmpty())
                     .collect(Collectors.toSet());
    }
}
