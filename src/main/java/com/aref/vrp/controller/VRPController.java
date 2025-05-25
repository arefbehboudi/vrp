package com.aref.vrp.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aref.vrp.service.VRPService;

@RestController
@RequestMapping("/api/vrp")
public class VRPController {
    
    private final VRPService vrpService;

    public VRPController(VRPService vrpService) {
        this.vrpService = vrpService;
    }

    @GetMapping("/solve")
    public ResponseEntity<Map<Integer, List<Long>>> solveVRP() {
        try {
            Map<Integer, List<Long>> result = vrpService.solveVRP();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
