package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Repository.CompanyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EntityResolutionService {

    private final CompanyRepository companyRepository;

    // Threshold for similarity (0.0 to 1.0)
    // 0.85 means the strings must be at least 85% similar to be considered a match
    private static final double SIMILARITY_THRESHOLD = 0.85;

    public EntityResolutionService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * Găsește o companie existentă folosind potrivire exactă, apoi potrivire aproximativă (Fuzzy Matching).
     * Dacă nu găsește, o creează.
     */
    public Company findOrCreateCompany(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "Unknown Company";
        }

        String normalizedInput = normalizeCompanyName(name);

        // 1. Încercăm o potrivire exactă (cea mai rapidă)
        Optional<Company> exactMatch = companyRepository.findByName(name.trim().toUpperCase());
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        // 2. Extragem toate companiile pentru a verifica similaritatea
        // (Pentru baze de date foarte mari ar trebui să folosim un full-text search index în Neo4j)
        List<Company> allCompanies = companyRepository.findAll();
        
        Company bestMatch = null;
        double highestSimilarity = 0.0;

        for (Company existingCompany : allCompanies) {
            String existingName = existingCompany.getName();
            String normalizedExisting = normalizeCompanyName(existingName);

            double similarity = calculateSimilarity(normalizedInput, normalizedExisting);

            if (similarity > highestSimilarity) {
                highestSimilarity = similarity;
                bestMatch = existingCompany;
            }
        }

        // 3. Dacă cea mai bună potrivire depășește pragul, considerăm că am găsit compania
        if (highestSimilarity >= SIMILARITY_THRESHOLD && bestMatch != null) {
            return bestMatch;
        }

        // 4. Dacă nu există nicio companie similară, creăm una nouă (cu numele ei original formatat UPPERCASE)
        Company newCompany = new Company(name.trim().toUpperCase());
        return companyRepository.save(newCompany);
    }

    /**
     * Normalizează numele companiei pentru comparație:
     * - litere mici
     * - elimină sufixe legale ("srl", "inc", "llc", "ltd")
     * - elimină locații generice ("romania", "uk", "usa")
     * - elimină punctuația (",", ".", "-")
     */
    private String normalizeCompanyName(String name) {
        if (name == null) return "";
        
        String n = name.toLowerCase();
        
        // Eliminăm caractere speciale care pot încurca potrivirea
        n = n.replaceAll("[\\.,\\-']", " ");
        
        // Eliminăm sufixe comune (se pun cu spații la margini pentru a nu tăia din interiorul altor cuvinte)
        String[] suffixesToRemove = {" srl", " s.r.l.", " s r l", " inc", " llc", " ltd", " gmbh", " ag", " sa", " s.a.", " romania", " uk", " usa", " global", " group"};
        for (String suffix : suffixesToRemove) {
            if (n.endsWith(suffix)) {
                n = n.substring(0, n.length() - suffix.length());
            } else {
                n = n.replace(suffix + " ", " ");
            }
        }
        
        // Eliminăm spațiile multiple rămase
        return n.replaceAll("\\s+", " ").trim();
    }

    /**
     * Calculează similaritatea bazată pe distanța Levenshtein.
     * Returnează un scor de la 0.0 (complet diferite) la 1.0 (identice).
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }
        if (s1.length() == 0 || s2.length() == 0) {
            return 0.0;
        }
        
        int distance = computeLevenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());
        
        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Algoritmul standard Levenshtein Distance (numărul minim de editări
     * necesare pentru a transforma string-ul s1 în s2).
     */
    private int computeLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
                }
            }
        }
        return dp[s1.length()][s2.length()];
    }
}
