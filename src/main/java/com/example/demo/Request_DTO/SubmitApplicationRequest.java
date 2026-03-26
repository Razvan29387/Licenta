package com.example.demo.Request_DTO;

public class SubmitApplicationRequest {
    private String applicantName;
    private String candidateCv;

    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }

    public String getCandidateCv() { return candidateCv; }
    public void setCandidateCv(String candidateCv) { this.candidateCv = candidateCv; }
}