package com.example.demo.Config;

import com.example.demo.Entity.Firma;
import com.example.demo.Repository.FirmaRepository;
import com.example.demo.Entity.Person;
import com.example.demo.Repository.PersonRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final PersonRepository personRepository;
    private final FirmaRepository firmaRepository;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public DataInitializer(PersonRepository userRepository,
                           FirmaRepository firmaRepository,
                           ResourceLoader resourceLoader) {
        this.personRepository = userRepository;
        this.firmaRepository = firmaRepository;
        this.resourceLoader = resourceLoader;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void run(String... args) throws Exception {
        if (personRepository.count() == 0) {
            log.info("User repository is empty. Initializing user data...");
            // Aici pot fi adăugate metode de încărcare a datelor pentru Person, dacă este necesar
            log.info("User data initialization finished. Total users in DB: {}", personRepository.count());
        } else {
            log.info("User repository already contains data. Skipping initialization.");
        }

        synchronizeFirmeFromJson("classpath:firme.json");
    }

    private void synchronizeFirmeFromJson(String filePath) throws IOException {
        log.info("Synchronizing firms from JSON file: {}", filePath);
        Resource resource = resourceLoader.getResource(filePath);
        List<Firma> firmeFromJson = objectMapper.readValue(resource.getInputStream(), new TypeReference<List<Firma>>() {});

        Set<String> existingFirmaNames = firmaRepository.findAll().stream()
                .map(Firma::getName)
                .collect(Collectors.toSet());

        List<Firma> firmeToSave = firmeFromJson.stream()
                .filter(firma -> !existingFirmaNames.contains(firma.getName()))
                .collect(Collectors.toList());

        if (!firmeToSave.isEmpty()) {
            firmaRepository.saveAll(firmeToSave);
            log.info("Added {} new firms to the database.", firmeToSave.size());
        } else {
            log.info("No new firms to add. Database is up to date.");
        }
    }
}