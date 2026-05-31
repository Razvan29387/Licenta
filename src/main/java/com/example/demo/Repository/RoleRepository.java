package com.example.demo.Repository;

import com.example.demo.Entity.ERole;
import com.example.demo.Entity.Role;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.Optional;

public interface RoleRepository extends Neo4jRepository<Role, Long> {
    Optional<Role> findByName(ERole name);
}
