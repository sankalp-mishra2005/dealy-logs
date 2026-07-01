package com.bsp.delay.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DelayLogRequest {

    @NotNull(message = "Area ID is required")
    private Long areaId;

    @NotNull(message = "Delay Type ID is required")
    private Long delayTypeId;

    private Long reasonId;

    @NotNull(message = "Log date is required")
    private LocalDate logDate;

    @NotBlank(message = "Start time is required")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Start time must be in HH:MM format")
    private String startTime;

    @NotBlank(message = "End time is required")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "End time must be in HH:MM format")
    private String endTime;

    @NotBlank(message = "Shift is required")
    private String shift;

    @NotBlank(message = "Remarks are required")
    @Size(max = 4000, message = "Remarks cannot exceed 4000 characters")
    private String remarks;
}
