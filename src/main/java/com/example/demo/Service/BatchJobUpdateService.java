package com.example.demo.Service;

import com.example.demo.Entity.Company;
import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class BatchJobUpdateService {

    private static final Logger log = LoggerFactory.getLogger(BatchJobUpdateService.class);
    private static final int BATCH_SIZE = 500;

    private final JobRepository jobRepository;
    private final EntityResolutionService entityResolutionService;
    private final NerExtractionService nerExtractionService;

    public BatchJobUpdateService(JobRepository jobRepository, EntityResolutionService entityResolutionService, NerExtractionService nerExtractionService) {
        this.jobRepository = jobRepository;
        this.entityResolutionService = entityResolutionService;
        this.nerExtractionService = nerExtractionService;
    }

    @Transactional("transactionManager")
    public void updateAllJobsWithRemoteAndContractType() {
        log.info("Batch update: Starting inference of remote status and contract type for existing jobs...");
        
        List<Job> allJobs = jobRepository.findAll();
        int updateCount = 0;

        Pattern remotePattern = Pattern.compile("(?i)\\b(remote|wfh|work from home|telecommute)\\b");
        Pattern fullTimePattern = Pattern.compile("(?i)\\b(full-time|full time|permanent)\\b");
        Pattern partTimePattern = Pattern.compile("(?i)\\b(part-time|part time)\\b");
        Pattern contractPattern = Pattern.compile("(?i)\\b(contract|contractor|freelance)\\b");

        for (Job job : allJobs) {
            boolean changed = false;

            String searchString = "";
            if (job.getTitle() != null) searchString += job.getTitle() + " ";
            if (job.getDescription() != null) searchString += job.getDescription() + " ";
            if (job.getLocation() != null) searchString += job.getLocation();

            if (job.getJobIsRemote() == null) {
                if (remotePattern.matcher(searchString).find() || "Remote".equalsIgnoreCase(job.getCountry())) {
                    job.setJobIsRemote(true);
                    changed = true;
                } else if (!searchString.isEmpty()) {
                    job.setJobIsRemote(false);
                    changed = true;
                }
            }

            if (job.getContractType() == null || job.getContractType().isEmpty()) {
                if (fullTimePattern.matcher(searchString).find()) {
                    job.setContractType("full_time");
                    changed = true;
                } else if (partTimePattern.matcher(searchString).find()) {
                    job.setContractType("part_time");
                    changed = true;
                } else if (contractPattern.matcher(searchString).find()) {
                    job.setContractType("contract");
                    changed = true;
                }
            }

            if (changed) {
                jobRepository.save(job);
                updateCount++;
                if (updateCount % 1000 == 0) {
                     log.info("Batch update: Processed {} jobs so far...", updateCount);
                }
            }
        }

        log.info("Batch update finished: Successfully inferred data for {} jobs.", updateCount);
    }

    @Async("taskExecutor")
    @Transactional("transactionManager")
    public void reprocessAllJobRelationships() {
        log.info("--- STARTING BATCH REPROCESSING OF COMPANY RELATIONSHIPS ---");
        
        int page = 0;
        Page<Job> jobPage;
        int totalProcessed = 0;

        do {
            Pageable pageable = PageRequest.of(page, BATCH_SIZE);
            jobPage = jobRepository.findAll(pageable);
            List<Job> jobsToProcess = jobPage.getContent();

            if (!jobsToProcess.isEmpty()) {
                log.info("Reprocessing batch {} ({} jobs)...", page, jobsToProcess.size());
                
                for (Job job : jobsToProcess) {
                    if (job.getCompanyName() != null && !job.getCompanyName().isBlank()) {
                        Company company = entityResolutionService.findOrCreateCompany(job.getCompanyName());
                        job.setCompany(company);
                    }
                }
                
                jobRepository.saveAll(jobsToProcess);

                totalProcessed += jobsToProcess.size();
                log.info("Processed {} jobs for company linking.", totalProcessed);
            }
            page++;
        } while (jobPage.hasNext());

        log.info("--- FINISHED REPROCESSING COMPANY RELATIONSHIPS ---");
    }

    @Transactional("transactionManager")
    public void cleanHtmlFromDescriptions() {
        log.info("Batch update: Starting HTML cleanup from job descriptions in batches...");
        
        int page = 0;
        Page<Job> jobPage;
        int totalProcessed = 0;
        int totalCleaned = 0;

        do {
            Pageable pageable = PageRequest.of(page, BATCH_SIZE);
            jobPage = jobRepository.findAll(pageable);
            List<Job> jobsToProcess = jobPage.getContent();

            if (!jobsToProcess.isEmpty()) {
                boolean batchChanged = false;
                for (Job job : jobsToProcess) {
                    if (job.getDescription() != null && job.getDescription().matches(".*<[a-zA-Z]+.*?>.*")) {
                        String cleanDescription = Jsoup.parse(job.getDescription()).text();
                        job.setDescription(cleanDescription);
                        batchChanged = true;
                        totalCleaned++;
                    }
                }
                
                if (batchChanged) {
                    jobRepository.saveAll(jobsToProcess);
                }
                totalProcessed += jobsToProcess.size();
                log.info("Processed {} jobs, cleaned {}", totalProcessed, totalCleaned);
            }
            page++;
        } while (jobPage.hasNext());

        log.info("Batch HTML cleanup finished. Cleaned descriptions for {} out of {} total jobs.", totalCleaned, totalProcessed);
    }
}
