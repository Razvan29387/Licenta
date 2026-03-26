package com.example.demo.Controller;

import com.example.demo.Entity.Firma;
import com.example.demo.Repository.FirmaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/firme") // Updated to include /api prefix
public class FirmaController {

    private final FirmaRepository firmaRepository;

    public FirmaController(FirmaRepository firmaRepository) {
        this.firmaRepository = firmaRepository;
    }

    @GetMapping
    public List<Firma> getAllFirme() {
        return firmaRepository.findAll();
    }
}