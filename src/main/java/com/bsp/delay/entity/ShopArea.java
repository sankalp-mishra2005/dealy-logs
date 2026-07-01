package com.bsp.delay.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "SHOP_AREA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopArea {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "shop_area_seq_gen")
    @SequenceGenerator(name = "shop_area_seq_gen", sequenceName = "shop_area_seq", allocationSize = 1)
    @Column(name = "area_id")
    private Long areaId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "area_name", nullable = false)
    private String areaName;

    @Column(name = "area_code", nullable = false, unique = true)
    private String areaCode;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
