package com.bsp.delay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "SHIFT_OPERATIONAL_LOG",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_op_log_date_area", columnNames = {"report_date", "area_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftOperationalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "operational_log_seq_gen")
    @SequenceGenerator(name = "operational_log_seq_gen", sequenceName = "operational_log_seq", allocationSize = 1)
    @Column(name = "operational_log_id")
    private Long operationalLogId;

    @Column(name = "report_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(name = "available_hours", nullable = false)
    private Double availableHours;

    @Column(name = "production_tonnage", nullable = false)
    private Double productionTonnage;
}
