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

    @Query(value = "MATCH (j:Job) WHERE toLower(j.country) = toLower($country) " +
                   "WITH j ORDER BY j.createdAt DESC SKIP $skip LIMIT $limit " +
                   "OPTIONAL MATCH (j)-[r:POSTED_BY]->(c:Company) " +
                   "RETURN j, collect(r), collect(c)",
           countQuery = "MATCH (j:Job) WHERE toLower(j.country) = toLower($country) RETURN count(j)")
    Page<Job> findByCountry(@Param("country") String country, Pageable pageable);

    @Query(value = "MATCH (j:Job) WHERE toLower(j.category) = toLower($category) " +
                   "WITH j ORDER BY j.createdAt DESC SKIP $skip LIMIT $limit " +
                   "OPTIONAL MATCH (j)-[r:POSTED_BY]->(c:Company) " +
                   "RETURN j, collect(r), collect(c)",
           countQuery = "MATCH (j:Job) WHERE toLower(j.category) = toLower($category) RETURN count(j)")
    Page<Job> findByCategory(@Param("category") String category, Pageable pageable);

    @Query(value = "MATCH (j:Job) " +
           "WHERE toLower(j.title) CONTAINS toLower($keyword) " +
           "OR toLower(j.companyName) CONTAINS toLower($keyword) " +
           "OR toLower(j.location) CONTAINS toLower($keyword) " +
           "OR (j.description IS NOT NULL AND toLower(j.description) CONTAINS toLower($keyword)) " +
           "WITH j ORDER BY j.createdAt DESC SKIP $skip LIMIT $limit " +
           "OPTIONAL MATCH (j)-[r:POSTED_BY]->(c:Company) " +
           "RETURN j, collect(r), collect(c)",
           countQuery = "MATCH (j:Job) " +
                        "WHERE toLower(j.title) CONTAINS toLower($keyword) " +
                        "OR toLower(j.companyName) CONTAINS toLower($keyword) " +
                        "OR toLower(j.location) CONTAINS toLower($keyword) " +
                        "OR (j.description IS NOT NULL AND toLower(j.description) CONTAINS toLower($keyword)) " +
                        "RETURN count(j)")
    Page<Job> searchJobsByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("MATCH (j:Job)-[:POSTED_BY]->(c:Company) WHERE c.name = $companyName RETURN j, c")
    List<Job> findByCompanyName(@Param("companyName") String companyName);

    @Query("MATCH (j:Job)-[:POSTED_BY]->(c:Company) WHERE id(c) = $companyId RETURN j")
    List<Job> findByCompanyId(@Param("companyId") Long companyId);

    @Query("MATCH (j:Job) RETURN count(j)")
    long countJobs();

    @Query("MATCH (j:Job) WHERE j.createdAt IS NULL OR j.createdAt < $date RETURN id(j)")
    List<Long> findIdsByCreatedAtBefore(@Param("date") LocalDateTime date);
}
