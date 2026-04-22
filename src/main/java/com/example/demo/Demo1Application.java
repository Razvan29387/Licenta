package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Activează suportul pentru sarcini programate
public class Demo1Application {

    public static void main(String[] args) {
        SpringApplication.run(Demo1Application.class, args);
    }

    // CommandLineRunner a fost eliminat pentru a preveni rularea importului la fiecare pornire.
    // Această logică va fi mutată într-un serviciu programat.
}