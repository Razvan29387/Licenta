package com.example.demo.Service;

import com.example.demo.Entity.Job;
import com.example.demo.Entity.Skill;
import com.example.demo.Entity.Occupation;
import com.example.demo.Repository.JobRepository;
import com.example.demo.Repository.SkillRepository;
import com.example.demo.Repository.OccupationRepository;
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
    private final OccupationRepository occupationRepository;
    private final JobRepository jobRepository;
    private final GeminiAgentService geminiAgentService;

    // --- Gazetteers ---
    private static final Set<String> PROGRAMMING_LANGUAGES = new HashSet<>(Arrays.asList(
            "java", "python", "javascript", "typescript", "c++", "c#", "ruby", "go", "php", "swift", "kotlin", "scala", "rust", "sql"
    ));

    private static final Set<String> FRAMEWORKS = new HashSet<>(Arrays.asList(
            "react", "angular", "vue", "spring boot", "django", "flask", "express", "laravel", "rails", ".net", "node.js", "next.js"
    ));

    private static final Set<String> TECHNOLOGIES = new HashSet<>(Arrays.asList(
            "docker", "kubernetes", "aws", "azure", "gcp", "mysql", "postgresql", "mongodb", "redis", "elasticsearch", "git", "jenkins", "terraform", "linux"
    ));

    private static final Set<String> OCCUPATIONS = new HashSet<>(Arrays.asList(
            "software engineer", "developer", "data scientist", "data analyst", "devops engineer", 
            "product manager", "project manager", "qa engineer", "system administrator", 
            "cloud architect", "security analyst", "cyber security", "frontend developer", 
            "backend developer", "fullstack developer", "mobile developer", "ui/ux designer",
            "machine learning engineer", "ai engineer"
    ));

    private static final Set<String> ALL_KNOWN_SKILLS = new HashSet<>();
    static {
        ALL_KNOWN_SKILLS.addAll(PROGRAMMING_LANGUAGES);
        ALL_KNOWN_SKILLS.addAll(FRAMEWORKS);
        ALL_KNOWN_SKILLS.addAll(TECHNOLOGIES);
    }

    public NerExtractionService(SkillRepository skillRepository, OccupationRepository occupationRepository, JobRepository jobRepository, GeminiAgentService geminiAgentService) {
        this.skillRepository = skillRepository;
        this.occupationRepository = occupationRepository;
        this.jobRepository = jobRepository;
        this.geminiAgentService = geminiAgentService;
    }

    @Async("taskExecutor")
    public void processJob(Job job) {
        if (job.getDescription() == null || job.getDescription().isEmpty()) {
            return;
        }

        // --- Step 1: Rule-based extraction (Gazetteer matching) ---
        // Skills (from description)
        Set<String> extractedSkills = extractKeywords(job.getDescription(), ALL_KNOWN_SKILLS);
        Set<Skill> linkedSkills = new HashSet<>();
        for (String keyword : extractedSkills) {
            linkedSkills.add(findOrCreateSkill(keyword));
        }

        // Occupations (from title and description)
        String titleAndDesc = job.getTitle() + " " + job.getDescription();
        Set<String> extractedOccupations = extractKeywords(titleAndDesc, OCCUPATIONS);
        Set<Occupation> linkedOccupations = new HashSet<>();
        for (String keyword : extractedOccupations) {
            linkedOccupations.add(findOrCreateOccupation(keyword));
        }

        log.info("Job ID {}: Extracted {} skills and {} occupations from dictionaries.", job.getId(), linkedSkills.size(), linkedOccupations.size());

        // --- Step 2: LLM-based extraction for complex/soft skills ---
        try {
            Set<String> llmExtractedSkills = askLLMForComplexEntities(job.getDescription(), extractedSkills);
            for (String skillName : llmExtractedSkills) {
                if (!skillName.isEmpty()) {
                    linkedSkills.add(findOrCreateSkill(skillName));
                }
            }
            log.info("Job ID {}: Added {} skills from LLM analysis.", job.getId(), llmExtractedSkills.size());
        } catch (Exception e) {
            log.error("Job ID {}: Failed to process LLM extraction. Reason: {}", job.getId(), e.getMessage());
        }

        // --- Final Step: Update the job with all linked entities ---
        job.setSkills(linkedSkills);
        job.setOccupations(linkedOccupations);
        jobRepository.save(job);
        
        log.info("Finished NER processing for job ID {}. Linked {} skills, {} occupations.", job.getId(), linkedSkills.size(), linkedOccupations.size());
    }

    private Set<String> extractKeywords(String text, Set<String> dictionary) {
        Set<String> found = new HashSet<>();
        String lowerText = " " + text.toLowerCase().replaceAll("[^a-z0-9+#.\\-]", " ") + " ";

        for (String item : dictionary) {
            String pattern = ".*\\b" + Pattern.quote(item) + "\\b.*";
            if (lowerText.matches(pattern)) {
                found.add(item);
            }
        }
        return found;
    }

    private Skill findOrCreateSkill(String name) {
        String normalizedName = normalizeName(name);

        Optional<Skill> exactMatch = skillRepository.findByName(normalizedName);
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        Iterable<Skill> allSkills = skillRepository.findAll();
        for (Skill existingSkill : allSkills) {
            if (calculateSimilarity(normalizedName, existingSkill.getName()) > 0.90) {
                return existingSkill;
            }
        }

        Skill newSkill = new Skill(normalizedName);
        return skillRepository.save(newSkill);
    }

    private Occupation findOrCreateOccupation(String name) {
        String normalizedName = normalizeName(name);

        Optional<Occupation> exactMatch = occupationRepository.findByName(normalizedName);
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        Iterable<Occupation> allOccupations = occupationRepository.findAll();
        for (Occupation existingOcc : allOccupations) {
            if (calculateSimilarity(normalizedName, existingOcc.getName()) > 0.90) {
                return existingOcc;
            }
        }

        Occupation newOcc = new Occupation(normalizedName);
        return occupationRepository.save(newOcc);
    }

    private String normalizeName(String name) {
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
        
        if (response == null || response.trim().isEmpty() || response.toLowerCase().contains("error") || response.toLowerCase().contains("simulated")) {
            return new HashSet<>();
        }

        return Arrays.stream(response.split(","))
                     .map(String::trim)
                     .filter(s -> !s.isEmpty())
                     .collect(Collectors.toSet());
    }
}
