package com.bsp.delay.controller;

import com.bsp.delay.dto.ApiResponse;
import com.bsp.delay.dto.AreaInputRequest;
import com.bsp.delay.entity.ShiftOperationalLog;
import com.bsp.delay.service.OperationalInputService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/operational-input")
public class OperationalInputController {

    private final OperationalInputService operationalInputService;

    public OperationalInputController(OperationalInputService operationalInputService) {
        this.operationalInputService = operationalInputService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShiftOperationalLog>> saveOperationalInput(@Valid @RequestBody AreaInputRequest request) {
        ShiftOperationalLog saved = operationalInputService.saveOperationalInput(request);
        return ResponseEntity.ok(ApiResponse.success(saved, "Operational input saved successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ShiftOperationalLog>> getOperationalInput(
            @RequestParam Long areaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        return operationalInputService.getOperationalInputByAreaAndDate(areaId, date)
                .map(input -> ResponseEntity.ok(ApiResponse.success(input, "Operational input retrieved successfully")))
                .orElseGet(() -> {
                    // Return a default fallback container so the frontend loads safely
                    ShiftOperationalLog fallback = ShiftOperationalLog.builder()
                            .areaId(areaId)
                            .logDate(date)
                            .availableHours(24.0)
                            .productionTonnage(0.0)
                            .build();
                    return ResponseEntity.ok(ApiResponse.success(fallback, "Default fallback inputs returned"));
                });
    }
}
