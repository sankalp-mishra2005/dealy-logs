package com.bsp.delay.repository;

import com.bsp.delay.entity.ShiftOperationalLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ShiftOperationalLogRepository extends JpaRepository<ShiftOperationalLog, Long> {
    Optional<ShiftOperationalLog> findByAreaIdAndLogDate(Long areaId, LocalDate logDate);
    List<ShiftOperationalLog> findByAreaIdAndLogDateBetween(Long areaId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT sol FROM ShiftOperationalLog sol, ShopArea sa " +
           "WHERE sol.areaId = sa.areaId " +
           "AND sa.shopId = :shopId " +
           "AND sol.logDate BETWEEN :startDate AND :endDate " +
           "AND (:areaId IS NULL OR sol.areaId = :areaId)")
    List<ShiftOperationalLog> findFilteredOperationalLogs(
           @Param("shopId") Long shopId,
           @Param("startDate") LocalDate startDate,
           @Param("endDate") LocalDate endDate,
           @Param("areaId") Long areaId);
}
