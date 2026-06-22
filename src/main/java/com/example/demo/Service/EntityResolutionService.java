package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Repository.CompanyRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EntityResolutionService {

    private static final Logger log = LoggerFactory.getLogger(EntityResolutionService.class);
    private final CompanyRepository companyRepository;
    private final Map<String, Company> companyCache = new ConcurrentHashMap<>();

    private static final double SIMILARITY_THRESHOLD = 0.90;

    public EntityResolutionService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing Company cache...");
        List<Company> allCompanies = companyRepository.findAll();
        for (Company company : allCompanies) {
            companyCache.put(company.getName().toUpperCase(), company);
        }
        log.info("Company cache initialized with {} entries.", companyCache.size());
    }

    public Company findOrCreateCompany(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "Unknown Company";
        }
        String formattedName = name.trim().toUpperCase();

        if (companyCache.containsKey(formattedName)) {
            return companyCache.get(formattedName);
        }

        Optional<Company> exactMatchDb = companyRepository.findByName(formattedName);
        if (exactMatchDb.isPresent()) {
            companyCache.put(formattedName, exactMatchDb.get());
            return exactMatchDb.get();
        }

        String normalizedInput = normalizeCompanyName(formattedName);
        for (Company existingCompany : companyCache.values()) {
            String normalizedExisting = normalizeCompanyName(existingCompany.getName());
            if (calculateSimilarity(normalizedInput, normalizedExisting) >= SIMILARITY_THRESHOLD) {
                log.debug("Fuzzy match found: '{}' -> '{}'", name, existingCompany.getName());
                companyCache.put(formattedName, existingCompany);
                return existingCompany;
            }
        }

        log.info("No similar company found. Creating new company: {}", formattedName);
        Company newCompany = new Company(formattedName);
        Company savedCompany = companyRepository.save(newCompany);
        companyCache.put(formattedName, savedCompany);
        return savedCompany;
    }

    private String normalizeCompanyName(String name) {
        if (name == null) return "";
        String n = name.toLowerCase()
                .replaceAll("[\\.,\\-']", " ")
                .replaceAll("\\s+(srl|s\\.r\\.l|inc|llc|ltd|gmbh|ag|sa|s\\.a|romania|uk|usa|global|group|corp|corporation)\\b", "");
        return n.replaceAll("\\s+", " ").trim();
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
}
