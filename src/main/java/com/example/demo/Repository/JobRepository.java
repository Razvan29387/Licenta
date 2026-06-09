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

    @Query(value = "MATCH (j:Job) " +
           "WHERE ($keyword = '' OR j.title IS NOT NULL AND toLower(j.title) CONTAINS $keyword OR j.companyName IS NOT NULL AND toLower(j.companyName) CONTAINS $keyword OR j.location IS NOT NULL AND toLower(j.location) CONTAINS $keyword) " +
           "AND ($country = '' OR j.country IS NOT NULL AND toLower(j.country) = $country) " +
           "AND ($category = '' OR j.category IS NOT NULL AND toLower(j.category) = $category) " +
           "RETURN j",
           countQuery = "MATCH (j:Job) " +
                        "WHERE ($keyword = '' OR j.title IS NOT NULL AND toLower(j.title) CONTAINS $keyword OR j.companyName IS NOT NULL AND toLower(j.companyName) CONTAINS $keyword OR j.location IS NOT NULL AND toLower(j.location) CONTAINS $keyword) " +
                        "AND ($country = '' OR j.country IS NOT NULL AND toLower(j.country) = $country) " +
                        "AND ($category = '' OR j.category IS NOT NULL AND toLower(j.category) = $category) " +
                        "RETURN count(j)")
    Page<Job> searchAndFilterJobs(@Param("keyword") String keyword, @Param("country") String country, @Param("category") String category, Pageable pageable);

    @Query("MATCH (j:Job)-[:POSTED_BY]->(c:Company) WHERE c.name = $companyName RETURN j, c")
    List<Job> findByCompanyName(@Param("companyName") String companyName);

    @Query("MATCH (j:Job)-[:POSTED_BY]->(c:Company) WHERE id(c) = $companyId RETURN j")
    List<Job> findByCompanyId(@Param("companyId") Long companyId);

    @Query("MATCH (j:Job) RETURN count(j)")
    long countJobs();

    @Query("MATCH (j:Job) " +
           "WHERE j.createdAt IS NULL " +
           "OR datetime(j.createdAt) < $date " +
           "RETURN j")
    List<Job> findByCreatedAtBefore(@Param("date") LocalDateTime date);
}
