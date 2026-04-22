package com.example.demo.Repository;

import com.example.demo.Entity.Person;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface PersonRepository extends Neo4jRepository<Person, String> {

    Person findByName(String name);

    void deleteByName(String name);
}