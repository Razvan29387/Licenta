package com.example.demo.Repository;

import com.example.demo.Entity.Occupation;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import java.util.Optional;

public interface OccupationRepository extends Neo4jRepository<Occupation, Long> {
    Optional<Occupation> findByName(String name);
}
