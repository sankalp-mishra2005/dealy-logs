package com.bsp.delay.controller;

import com.bsp.delay.dto.ApiResponse;
import com.bsp.delay.entity.DelayReason;
import com.bsp.delay.service.DelayReasonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/delay-reasons")
public class DelayReasonController {

    private final DelayReasonService delayReasonService;

    public DelayReasonController(DelayReasonService delayReasonService) {
        this.delayReasonService = delayReasonService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DelayReason>>> getDelayReasons(
            @RequestParam Long shopId,
            @RequestParam Long delayTypeId) {
        List<DelayReason> reasons = delayReasonService.getDelayReasons(shopId, delayTypeId);
        return ResponseEntity.ok(ApiResponse.success(reasons, "Delay reasons retrieved successfully"));
    }
}
