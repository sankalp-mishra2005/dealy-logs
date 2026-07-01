package com.bsp.delay.repository;

import com.bsp.delay.entity.DelayLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DelayLogRepository extends JpaRepository<DelayLog, Long> {
    List<DelayLog> findByOperationalLogId(Long operationalLogId);
    List<DelayLog> findByOperationalLogIdIn(List<Long> operationalLogIds);

    @Query("SELECT dl FROM DelayLog dl, ShiftOperationalLog sol, ShopArea sa " +
           "WHERE dl.operationalLogId = sol.operationalLogId AND sol.areaId = sa.areaId " +
           "AND sa.shopId = :shopId " +
           "AND sol.logDate BETWEEN :startDate AND :endDate " +
           "AND (:areaId IS NULL OR sol.areaId = :areaId) " +
           "AND (:shift IS NULL OR dl.shift = :shift) " +
           "AND (:delayTypeId IS NULL OR dl.delayTypeId = :delayTypeId)")
    List<DelayLog> findFilteredDelays(
           @Param("shopId") Long shopId,
           @Param("startDate") LocalDate startDate,
           @Param("endDate") LocalDate endDate,
           @Param("areaId") Long areaId,
           @Param("shift") String shift,
           @Param("delayTypeId") Long delayTypeId);
}
