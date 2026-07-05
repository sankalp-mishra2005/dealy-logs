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
