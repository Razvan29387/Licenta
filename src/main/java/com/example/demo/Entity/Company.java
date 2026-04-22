package com.example.demo.Entity;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node
public class Company {
    @Id @GeneratedValue
    private Long id;

    private String name;
    
    private String website; 

    public Company() {
        // Constructor gol necesar pentru deserializare JSON
    }

    public Company(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}