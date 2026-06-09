package com.example.demo.Service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WebSocketProgressService {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketProgressService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendProgress(String status, String title, String reasonOrSkills) {
        Map<String, String> message = new HashMap<>();
        message.put("status", status); // "PROCESSING", "SAVED", "SKIPPED", "ERROR"
        message.put("jobTitle", title);
        message.put("details", reasonOrSkills);
        
        messagingTemplate.convertAndSend("/topic/demo-progress", message);
    }
}
