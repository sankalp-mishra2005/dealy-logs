package com.bsp.delay.service.impl;

import com.bsp.delay.dto.AreaInputRequest;
import com.bsp.delay.entity.ShiftOperationalLog;
import com.bsp.delay.entity.ShopArea;
import com.bsp.delay.exception.ResourceNotFoundException;
import com.bsp.delay.repository.ShiftOperationalLogRepository;
import com.bsp.delay.repository.ShopAreaRepository;
import com.bsp.delay.service.OperationalInputService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Optional;

@Service
@Transactional
public class OperationalInputServiceImpl implements OperationalInputService {

    private final ShiftOperationalLogRepository shiftOperationalLogRepository;
    private final ShopAreaRepository shopAreaRepository;

    public OperationalInputServiceImpl(ShiftOperationalLogRepository shiftOperationalLogRepository,
                                       ShopAreaRepository shopAreaRepository) {
        this.shiftOperationalLogRepository = shiftOperationalLogRepository;
        this.shopAreaRepository = shopAreaRepository;
    }

    @Override
    public ShiftOperationalLog saveOperationalInput(AreaInputRequest request) {
        // Validate area existence
        ShopArea area = shopAreaRepository.findById(request.getAreaId())
                .orElseThrow(() -> new ResourceNotFoundException("Shop Area not found for ID: " + request.getAreaId()));

        Optional<ShiftOperationalLog> existingLogOpt = shiftOperationalLogRepository
                .findByAreaIdAndLogDate(request.getAreaId(), request.getLogDate());

        ShiftOperationalLog log;
        if (existingLogOpt.isPresent()) {
            log = existingLogOpt.get();
            log.setAvailableHours(request.getAvailableHours());
            log.setProductionTonnage(request.getProductionTonnage());
        } else {
            log = ShiftOperationalLog.builder()
                    .areaId(request.getAreaId())
                    .logDate(request.getLogDate())
                    .availableHours(request.getAvailableHours())
                    .productionTonnage(request.getProductionTonnage())
                    .build();
        }

        return shiftOperationalLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShiftOperationalLog> getOperationalInputByAreaAndDate(Long areaId, LocalDate date) {
        return shiftOperationalLogRepository.findByAreaIdAndLogDate(areaId, date);
    }
}
