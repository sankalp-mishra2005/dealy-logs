package com.bsp.delay.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "SHOP")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shop {

    @Id
    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "shop_name", nullable = false)
    private String shopName;

    @Column(name = "shop_code", nullable = false, unique = true)
    private String shopCode;
}
