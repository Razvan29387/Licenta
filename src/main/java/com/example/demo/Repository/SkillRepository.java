package com.example.demo.Repository;

import com.example.demo.Entity.Skill;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import java.util.Optional;

public interface SkillRepository extends Neo4jRepository<Skill, Long> {
    Optional<Skill> findByName(String name);
}
