package com.example.demo.Entity;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

@Node
public class Job {
    @Id @GeneratedValue
    private Long id;

    private String adzunaId; // Unique ID from API or local UUID
    private String title;
    private String location;
    private String country; 
    private String description;
    private String url;
    private String category;
    
    // Câmpuri pentru detalii specifice joburilor
    private String experienceLevel; 
    private List<String> programmingLanguages;
    
    // Câmpuri pentru salariu
    private Double salaryMin;
    private Double salaryMax;
    private String salaryPeriod; // e.g., "year", "month", "hour"

    @Relationship(type = "POSTED_BY", direction = Relationship.Direction.OUTGOING)
    private Company company;

    public Job() {}

    // Constructor actualizat
    public Job(String adzunaId, String title, String location, String country, String url, String category, String description, Company company) {
        this.adzunaId = adzunaId;
        this.title = title;
        this.location = location;
        this.country = country;
        this.url = url;
        this.category = category;
        this.description = description;
        this.company = company;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getAdzunaId() { return adzunaId; }
    public void setAdzunaId(String adzunaId) { this.adzunaId = adzunaId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }
    public List<String> getProgrammingLanguages() { return programmingLanguages; }
    public void setProgrammingLanguages(List<String> programmingLanguages) { this.programmingLanguages = programmingLanguages; }
    public Double getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Double salaryMin) { this.salaryMin = salaryMin; }
    public Double getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Double salaryMax) { this.salaryMax = salaryMax; }
    public String getSalaryPeriod() { return salaryPeriod; }
    public void setSalaryPeriod(String salaryPeriod) { this.salaryPeriod = salaryPeriod; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
}