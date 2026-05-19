package com.example.demo.Repository;

import com.example.demo.Entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends Neo4jRepository<Job, Long> {
    boolean existsByAdzunaId(String adzunaId);
    Optional<Job> findByAdzunaId(String adzunaId);

    Page<Job> findByCountry(String country, Pageable pageable);
    Page<Job> findByCategory(String category, Pageable pageable);

    @Query(value = "MATCH (j:Job)-[r:POSTED_BY]->(c:Company) " +
                   "WHERE toLower(j.title) CONTAINS toLower($keyword) " +
                   "OR toLower(c.name) CONTAINS toLower($keyword) " +
                   "OR toLower(j.country) CONTAINS toLower($keyword) " +
                   "OR toLower(j.location) CONTAINS toLower($keyword) " +
                   "RETURN j, collect(r), collect(c)",
           countQuery = "MATCH (j:Job)-[:POSTED_BY]->(c:Company) " +
                        "WHERE toLower(j.title) CONTAINS toLower($keyword) " +
                        "OR toLower(c.name) CONTAINS toLower($keyword) " +
                        "OR toLower(j.country) CONTAINS toLower($keyword) " +
                        "OR toLower(j.location) CONTAINS toLower($keyword) " +
                        "RETURN count(j)")
    Page<Job> searchJobsByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("MATCH (j:Job)-[:POSTED_BY]->(c:Company) WHERE c.name = $companyName RETURN j, c")
    List<Job> findByCompanyName(@Param("companyName") String companyName);

    @Query("MATCH (j:Job) RETURN count(j)")
    long countJobs();

    List<Job> findByCreatedAtBefore(LocalDateTime date);
}