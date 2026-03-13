package com.printerhub.controller;

import com.printerhub.service.PushAllScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@CrossOrigin(origins = "http://localhost:4200",
             methods = {RequestMethod.GET, RequestMethod.PATCH, RequestMethod.OPTIONS})
@RequiredArgsConstructor
public class SettingsController {

    private final PushAllScheduler pushAllScheduler;

    @GetMapping
    public Map<String, Object> getSettings() {
        return Map.of("pushallIntervalSeconds", pushAllScheduler.getIntervalSeconds());
    }

    @PatchMapping("/pushall-interval")
    public Map<String, Object> setPushAllInterval(@RequestBody Map<String, Long> body) {
        long seconds = body.getOrDefault("seconds", 60L);
        seconds = Math.max(30, Math.min(300, seconds));   // clamp 30–300
        pushAllScheduler.setIntervalSeconds(seconds);
        return Map.of("pushallIntervalSeconds", pushAllScheduler.getIntervalSeconds());
    }
}
