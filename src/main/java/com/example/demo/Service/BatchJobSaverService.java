package com.example.demo.Service;

import com.example.demo.Entity.Job;
import com.example.demo.Repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BatchJobSaverService {

    private final JobRepository jobRepository;

    public BatchJobSaverService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Saves a list of jobs in a single, separate transaction.
     * This method is designed to be called for small batches of entities
     * to avoid transaction timeouts on large data ingestion tasks.
     * @param jobs The batch of jobs to save.
     */
    @Transactional
    public void saveJobsInBatch(List<Job> jobs) {
        jobRepository.saveAll(jobs);
    }
}