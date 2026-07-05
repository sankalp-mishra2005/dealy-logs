package com.bsp.delay.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "DELAY_REASON")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DelayReason {

    @Id
    @Column(name = "reason_id")
    private Long reasonId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "delay_type_id", nullable = false)
    private Long delayTypeId;

    @Column(name = "reason_name", nullable = false)
    private String reasonName;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
