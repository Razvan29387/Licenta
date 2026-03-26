package com.example.demo.Entity;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node
public class Application {
    @Id @GeneratedValue
    private Long id;

    private String applicantName;
    private String candidateCv;
    
    // AI Evaluation Fields
    private Integer aiScore;
    private String aiFeedback;

    @Relationship(type = "APPLIED_TO", direction = Relationship.Direction.OUTGOING)
    private Job job;

    public Application() {}

    public Application(String applicantName, String candidateCv, Job job) {
        this.applicantName = applicantName;
        this.candidateCv = candidateCv;
        this.job = job;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getCandidateCv() { return candidateCv; }
    public void setCandidateCv(String candidateCv) { this.candidateCv = candidateCv; }
    public Integer getAiScore() { return aiScore; }
    public void setAiScore(Integer aiScore) { this.aiScore = aiScore; }
    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }
    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }
}