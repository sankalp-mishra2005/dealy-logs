package com.bsp.delay.repository;

import com.bsp.delay.entity.DelayType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DelayTypeRepository extends JpaRepository<DelayType, Long> {
}
