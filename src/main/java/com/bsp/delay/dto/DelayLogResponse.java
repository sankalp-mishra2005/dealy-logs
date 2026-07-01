package com.bsp.delay.dto;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DelayLogResponse {
    private Long logId;
    private Long areaId;
    private String areaName;
    private Long delayTypeId;
    private String typeName;
    private String delayGroup;
    private Long reasonId;
    private String reasonName;
    private LocalDate logDate;
    private Integer delayHours;
    private Integer delayMinutes;
    private String remarks;
    private String durationHHMM;
    private String startTime;
    private String endTime;
    private String shift;
    private Integer durationMinutes;
}
