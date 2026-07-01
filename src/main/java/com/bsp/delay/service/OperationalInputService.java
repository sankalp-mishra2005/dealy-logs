package com.bsp.delay.service;

import com.bsp.delay.dto.AreaInputRequest;
import com.bsp.delay.entity.ShiftOperationalLog;
import java.time.LocalDate;
import java.util.Optional;

public interface OperationalInputService {
    ShiftOperationalLog saveOperationalInput(AreaInputRequest request);
    Optional<ShiftOperationalLog> getOperationalInputByAreaAndDate(Long areaId, LocalDate date);
}
