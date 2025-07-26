package com.luggagekeeper.keeper_app.common.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 테스트용 컨트롤러
 * 서버 동작 확인용
 */
@RestController
public class TestController {

    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Keeper App API Server is running!");
        response.put("timestamp", LocalDateTime.now());
        response.put("status", "OK");
        response.put("h2Console", "http://localhost:8080/h2-console");
        response.put("swaggerUI", "http://localhost:8080/swagger-ui/index.html");
        return response;
    }

    @GetMapping("/test/health")
    public Map<String, String> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("database", "H2 In-Memory");
        health.put("jpa", "Hibernate");
        return health;
    }
}