package com.example.demo.Repository;

import com.example.demo.Entity.Job;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends Neo4jRepository<Job, Long> {
    boolean existsByAdzunaId(String adzunaId);
    Optional<Job> findByAdzunaId(String adzunaId);

    @Query("MATCH (j:Job)-[:POSTED_BY]->(c:Company) WHERE c.name = $companyName RETURN j, c")
    List<Job> findByCompanyName(@Param("companyName") String companyName);

    @Query("MATCH (j:Job) RETURN count(j)")
    long countJobs();

    List<Job> findByCreatedAtBefore(LocalDateTime date);
}