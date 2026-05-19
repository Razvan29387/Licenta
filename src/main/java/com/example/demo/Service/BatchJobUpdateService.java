package com.example.demo.Service;

import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class BatchJobUpdateService {

    private static final Logger log = LoggerFactory.getLogger(BatchJobUpdateService.class);

    private final JobRepository jobRepository;

    public BatchJobUpdateService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Updates all existing jobs in the database to infer their remote status
     * and contract type from their descriptions or titles.
     * This is useful for migrating old data to the new schema format.
     */
    @Transactional
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

            // 1. Infer Remote Status
            if (job.getJobIsRemote() == null) {
                if (remotePattern.matcher(searchString).find() || "Remote".equalsIgnoreCase(job.getCountry())) {
                    job.setJobIsRemote(true);
                    changed = true;
                } else if (!searchString.isEmpty()) {
                    job.setJobIsRemote(false); // Only set to false if we actually searched something
                    changed = true;
                }
            }

            // 2. Infer Contract Type
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
}