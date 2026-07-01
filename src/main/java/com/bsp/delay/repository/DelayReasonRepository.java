package com.bsp.delay.repository;

import com.bsp.delay.entity.DelayReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DelayReasonRepository extends JpaRepository<DelayReason, Long> {
    List<DelayReason> findByShopIdAndDelayTypeIdOrderBySortOrderAsc(Long shopId, Long delayTypeId);
}
