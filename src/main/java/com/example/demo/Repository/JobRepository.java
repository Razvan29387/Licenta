package com.example.demo.Repository;

import com.example.demo.Entity.Job;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends Neo4jRepository<Job, Long> {
    boolean existsByAdzunaId(String adzunaId);
    Optional<Job> findByAdzunaId(String adzunaId);

    // New query to find jobs by company name
    @Query("MATCH (j:Job)-[:POSTED_BY]->(c:Company) WHERE c.name = $companyName RETURN j")
    List<Job> findByCompanyName(@Param("companyName") String companyName);
}