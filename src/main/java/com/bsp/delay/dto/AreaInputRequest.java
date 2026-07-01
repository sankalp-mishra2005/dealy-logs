package com.bsp.delay.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AreaInputRequest {

    @NotNull(message = "Area ID is required")
    private Long areaId;

    @NotNull(message = "Log date is required")
    private LocalDate logDate;

    @NotNull(message = "Available hours is required")
    @Min(value = 0, message = "Available hours cannot be negative")
    @Max(value = 24, message = "Available hours cannot exceed 24")
    private Double availableHours;

    @NotNull(message = "Production tonnage is required")
    @Min(value = 0, message = "Production tonnage cannot be negative")
    private Double productionTonnage;
}
