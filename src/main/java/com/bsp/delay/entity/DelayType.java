package com.bsp.delay.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "DELAY_TYPE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DelayType {

    @Id
    @Column(name = "delay_type_id")
    private Long delayTypeId;

    @Column(name = "type_name", nullable = false)
    private String typeName;

    @Column(name = "type_code", nullable = false, unique = true)
    private String typeCode;

    @Column(name = "delay_group", nullable = false)
    private String delayGroup;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
