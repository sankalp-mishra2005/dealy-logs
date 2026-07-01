package com.bsp.delay.controller;

import com.bsp.delay.dto.ApiResponse;
import com.bsp.delay.entity.DelayType;
import com.bsp.delay.service.DelayTypeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/delay-types")
public class DelayTypeController {

    private final DelayTypeService delayTypeService;

    public DelayTypeController(DelayTypeService delayTypeService) {
        this.delayTypeService = delayTypeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DelayType>>> getAllDelayTypes() {
        List<DelayType> delayTypes = delayTypeService.getAllDelayTypes();
        return ResponseEntity.ok(ApiResponse.success(delayTypes, "Delay types retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DelayType>> getDelayTypeById(@PathVariable Long id) {
        DelayType delayType = delayTypeService.getDelayTypeById(id);
        return ResponseEntity.ok(ApiResponse.success(delayType, "Delay type retrieved successfully"));
    }

}
