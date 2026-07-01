package com.bsp.delay.controller;

import com.bsp.delay.dto.ApiResponse;
import com.bsp.delay.dto.DelayLogRequest;
import com.bsp.delay.dto.DelayLogResponse;
import com.bsp.delay.service.DelayLogService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/delay-log")
public class DelayLogController {

    private final DelayLogService delayLogService;

    public DelayLogController(DelayLogService delayLogService) {
        this.delayLogService = delayLogService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DelayLogResponse>> saveDelayLog(@Valid @RequestBody DelayLogRequest request) {
        DelayLogResponse response = delayLogService.saveDelayLog(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Delay log saved successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DelayLogResponse>> updateDelayLog(
            @PathVariable Long id, @Valid @RequestBody DelayLogRequest request) {
        DelayLogResponse response = delayLogService.updateDelayLog(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Delay log updated successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DelayLogResponse>> getDelayLogById(@PathVariable Long id) {
        DelayLogResponse response = delayLogService.getDelayLogById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Delay log retrieved successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DelayLogResponse>>> getDelayLogs(
            @RequestParam Long areaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<DelayLogResponse> logs = delayLogService.getDelayLogsByAreaAndDate(areaId, date);
        return ResponseEntity.ok(ApiResponse.success(logs, "Delay logs retrieved successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDelayLog(@PathVariable Long id) {
        delayLogService.deleteDelayLog(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Delay log deleted successfully"));
    }
}
