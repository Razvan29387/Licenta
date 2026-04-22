package com.example.demo.Repository;

import com.example.demo.Entity.Company;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import java.util.Optional;

public interface CompanyRepository extends Neo4jRepository<Company, Long> {
    Optional<Company> findByName(String name);

    @Query("MATCH (c:Company) RETURN count(c)")
    long countCompanies();
}