package com.example.demo.Entity;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Node
public class Job {
    @Id @GeneratedValue
    private Long id;

    private String adzunaId;
    private String title;
    private String location;
    private String country;
    private String description;
    private String url;
    private String category;
    private LocalDateTime createdAt;
    private String experienceLevel;
    private List<String> programmingLanguages; // Kept for backward compatibility with old data
    private Double salaryMin;
    private Double salaryMax;
    private String salaryPeriod;
    private Boolean salaryIsPredicted;
    private List<String> locationArea;
    private String categoryTag;
    private String contractType;
    private String companyName;

    private String employerWebsite;
    private Boolean jobIsRemote;
    private LocalDateTime jobExpiresAt;
    private String jobSalaryCurrency;
    private Double latitude;
    private Double longitude;

    @Relationship(type = "POSTED_BY", direction = Relationship.Direction.OUTGOING)
    private Company company;

    @Relationship(type = "HAS_SKILL", direction = Relationship.Direction.OUTGOING)
    private Set<Skill> skills = new HashSet<>();

    public Job() {
        this.createdAt = LocalDateTime.now();
    }

    public Job(String adzunaId, String title, String location, String country, String url, String category, String description, Company company) {
        this.adzunaId = adzunaId;
        this.title = title;
        this.location = location;
        this.country = country;
        this.url = url;
        this.category = category;
        this.description = description;
        this.company = company;
        this.createdAt = LocalDateTime.now();
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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

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

    public Boolean getSalaryIsPredicted() { return salaryIsPredicted; }
    public void setSalaryIsPredicted(Boolean salaryIsPredicted) { this.salaryIsPredicted = salaryIsPredicted; }

    public List<String> getLocationArea() { return locationArea; }
    public void setLocationArea(List<String> locationArea) { this.locationArea = locationArea; }

    public String getCategoryTag() { return categoryTag; }
    public void setCategoryTag(String categoryTag) { this.categoryTag = categoryTag; }

    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getEmployerWebsite() { return employerWebsite; }
    public void setEmployerWebsite(String employerWebsite) { this.employerWebsite = employerWebsite; }

    public Boolean getJobIsRemote() { return jobIsRemote; }
    public void setJobIsRemote(Boolean jobIsRemote) { this.jobIsRemote = jobIsRemote; }

    public LocalDateTime getJobExpiresAt() { return jobExpiresAt; }
    public void setJobExpiresAt(LocalDateTime jobExpiresAt) { this.jobExpiresAt = jobExpiresAt; }

    public String getJobSalaryCurrency() { return jobSalaryCurrency; }
    public void setJobSalaryCurrency(String jobSalaryCurrency) { this.jobSalaryCurrency = jobSalaryCurrency; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Set<Skill> getSkills() { return skills; }
    public void setSkills(Set<Skill> skills) { this.skills = skills; }
}
