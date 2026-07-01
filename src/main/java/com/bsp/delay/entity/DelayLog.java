package com.bsp.delay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "DELAY_LOG")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DelayLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "delay_log_seq_gen")
    @SequenceGenerator(name = "delay_log_seq_gen", sequenceName = "delay_log_seq", allocationSize = 1)
    @Column(name = "delay_log_id")
    private Long delayLogId;

    @Column(name = "operational_log_id", nullable = false)
    private Long operationalLogId;

    @Column(name = "delay_type_id", nullable = false)
    private Long delayTypeId;

    @Column(name = "delay_hours", nullable = false)
    private Integer delayHours;

    @Column(name = "delay_minutes", nullable = false)
    private Integer delayMinutes;

    @Column(name = "delay_reason", nullable = false, length = 4000)
    private String remarks;

    @Column(name = "start_time", length = 10)
    private String startTime;

    @Column(name = "end_time", length = 10)
    private String endTime;

    @Column(name = "shift", length = 50)
    private String shift;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
