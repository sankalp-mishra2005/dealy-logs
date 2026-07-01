package com.bsp.delay.repository;

import com.bsp.delay.entity.ShopArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ShopAreaRepository extends JpaRepository<ShopArea, Long> {
    List<ShopArea> findByShopId(Long shopId);
}
