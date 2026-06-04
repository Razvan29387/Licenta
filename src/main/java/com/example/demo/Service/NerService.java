package com.example.demo.Service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NerService {

    private final Map<String, String> normalizationMap;
    private final Set<String> dictionary;

    public NerService() {
        this.normalizationMap = buildNormalizationMap();
        this.dictionary = buildDictionary();
    }

    /**
     * Extracts and normalizes entities from a given text based on a predefined dictionary.
     * @param text The job description or text to process.
     * @return A Set of unique, normalized entities found in the text.
     */
    public Set<String> extractEntities(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }

        // Normalize the input text for easier matching
        String normalizedText = " " + text.toLowerCase().replaceAll("[^a-zA-Z0-9#+.]", " ") + " ";

        Set<String> foundEntities = new HashSet<>();

        for (String keyword : dictionary) {
            // We search for the keyword surrounded by spaces to match whole words
            if (normalizedText.contains(" " + keyword + " ")) {
                // Use the normalization map to get the standard entity name
                foundEntities.add(normalizationMap.getOrDefault(keyword, keyword));
            }
        }
        
        return foundEntities;
    }

    /**
     * Builds the dictionary of all known keywords and their variations.
     */
    private Set<String> buildDictionary() {
        Set<String> dict = new HashSet<>();
        // Add all keys and values from the normalization map
        normalizationMap.forEach((key, value) -> {
            dict.add(key);
            dict.add(value);
        });
        return dict;
    }

    /**
     * Builds the map used for normalizing extracted entities to a standard form.
     * Key: the variation found in the text.
     * Value: the standard, canonical name for the entity.
     */
    private Map<String, String> buildNormalizationMap() {
        Map<String, String> map = new HashMap<>();

        // Programming Languages
        map.put("java", "java");
        map.put("java se", "java");
        map.put("java ee", "java");
        map.put("python", "python");
        map.put("c#", "c#");
        map.put("c sharp", "c#");
        map.put("c++", "c++");
        map.put("javascript", "javascript");
        map.put("js", "javascript");
        map.put("typescript", "typescript");
        map.put("ts", "typescript");
        map.put("php", "php");
        map.put("ruby", "ruby");
        map.put("go", "go");
        map.put("golang", "go");
        map.put("swift", "swift");
        map.put("kotlin", "kotlin");
        map.put("scala", "scala");
        map.put("rust", "rust");
        map.put("sql", "sql");
        map.put("pl/sql", "sql");
        map.put("plsql", "sql");
        map.put("t-sql", "sql");

        // Frameworks & Libraries
        map.put("spring", "spring");
        map.put("spring boot", "spring");
        map.put("springboot", "spring");
        map.put("react", "react");
        map.put("react.js", "react");
        map.put("reactjs", "react");
        map.put("angular", "angular");
        map.put("angular.js", "angular");
        map.put("vue", "vue");
        map.put("vue.js", "vue");
        map.put("django", "django");
        map.put("flask", "flask");
        map.put("node.js", "nodejs");
        map.put("nodejs", "nodejs");
        map.put("express", "express");
        map.put("express.js", "express");
        map.put(".net", ".net");
        map.put("dotnet", ".net");
        map.put("hibernate", "hibernate");
        map.put("jpa", "jpa");

        // Technologies & Concepts
        map.put("docker", "docker");
        map.put("kubernetes", "kubernetes");
        map.put("k8s", "kubernetes");
        map.put("aws", "aws");
        map.put("amazon web services", "aws");
        map.put("azure", "azure");
        map.put("microsoft azure", "azure");
        map.put("gcp", "gcp");
        map.put("google cloud platform", "gcp");
        map.put("microservices", "microservices");
        map.put("rest", "rest");
        map.put("restful", "rest");
        map.put("api", "api");
        map.put("apis", "api");
        map.put("git", "git");
        map.put("jenkins", "jenkins");
        map.put("ci/cd", "ci/cd");
        map.put("cicd", "ci/cd");
        map.put("linux", "linux");
        map.put("unix", "unix");
        map.put("kafka", "kafka");
        map.put("rabbitmq", "rabbitmq");
        map.put("graphql", "graphql");
        map.put("html", "html");
        map.put("css", "css");
        map.put("sass", "sass");
        map.put("less", "less");
        map.put("machine learning", "machine learning");
        map.put("ml", "machine learning");
        map.put("artificial intelligence", "ai");
        map.put("ai", "ai");

        return map;
    }
}
