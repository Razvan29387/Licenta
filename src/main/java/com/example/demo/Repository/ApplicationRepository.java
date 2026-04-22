package com.example.demo.Repository;

import com.example.demo.Entity.Application;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApplicationRepository extends Neo4jRepository<Application, Long> {
    
    @Query("MATCH (a:Application)-[:APPLIED_TO]->(j:Job) WHERE id(j) = $jobId RETURN a ORDER BY a.aiScore DESC")
    List<Application> findByJobIdOrderByAiScoreDesc(@Param("jobId") Long jobId);
}