package com.bsp.delay.service.impl;

import com.bsp.delay.dto.DelayLogRequest;
import com.bsp.delay.dto.DelayLogResponse;
import com.bsp.delay.entity.DelayLog;
import com.bsp.delay.entity.DelayType;
import com.bsp.delay.entity.ShiftOperationalLog;
import com.bsp.delay.entity.ShopArea;
import com.bsp.delay.entity.DelayReason;
import com.bsp.delay.exception.ResourceNotFoundException;
import com.bsp.delay.repository.DelayLogRepository;
import com.bsp.delay.repository.DelayReasonRepository;
import com.bsp.delay.repository.DelayTypeRepository;
import com.bsp.delay.repository.ShiftOperationalLogRepository;
import com.bsp.delay.repository.ShopAreaRepository;
import com.bsp.delay.service.DelayLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DelayLogServiceImpl implements DelayLogService {

    private final DelayLogRepository delayLogRepository;
    private final ShopAreaRepository shopAreaRepository;
    private final DelayTypeRepository delayTypeRepository;
    private final ShiftOperationalLogRepository shiftOperationalLogRepository;
    private final DelayReasonRepository delayReasonRepository;

    public DelayLogServiceImpl(DelayLogRepository delayLogRepository,
                               ShopAreaRepository shopAreaRepository,
                               DelayTypeRepository delayTypeRepository,
                               ShiftOperationalLogRepository shiftOperationalLogRepository,
                               DelayReasonRepository delayReasonRepository) {
        this.delayLogRepository = delayLogRepository;
        this.shopAreaRepository = shopAreaRepository;
        this.delayTypeRepository = delayTypeRepository;
        this.shiftOperationalLogRepository = shiftOperationalLogRepository;
        this.delayReasonRepository = delayReasonRepository;
    }

    private int calculateDuration(String startTime, String endTime) {
        String[] startParts = startTime.split(":");
        String[] endParts = endTime.split(":");
        int startMin = Integer.parseInt(startParts[0]) * 60 + Integer.parseInt(startParts[1]);
        int endMin = Integer.parseInt(endParts[0]) * 60 + Integer.parseInt(endParts[1]);
        int diff = endMin - startMin;
        if (diff < 0) {
            diff += 24 * 60; // Overnight
        }
        return diff;
    }

    @Override
    public DelayLogResponse saveDelayLog(DelayLogRequest request) {
        // Validate area and type existence
        ShopArea area = shopAreaRepository.findById(request.getAreaId())
                .orElseThrow(() -> new ResourceNotFoundException("Shop Area not found for ID: " + request.getAreaId()));
        DelayType type = delayTypeRepository.findById(request.getDelayTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Delay Type not found for ID: " + request.getDelayTypeId()));

        // Check if SHIFT_OPERATIONAL_LOG exists for this date and area
        ShiftOperationalLog opLog = shiftOperationalLogRepository
                .findByAreaIdAndLogDate(request.getAreaId(), request.getLogDate())
                .orElseThrow(() -> new IllegalArgumentException("No operational input found for this Area and Date. Please save operational inputs first."));

        int totalMinutes = calculateDuration(request.getStartTime(), request.getEndTime());
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;

        DelayLog log = DelayLog.builder()
                .operationalLogId(opLog.getOperationalLogId())
                .delayTypeId(request.getDelayTypeId())
                .reasonId(request.getReasonId())
                .delayHours(hours)
                .delayMinutes(mins)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .shift(request.getShift())
                .durationMinutes(totalMinutes)
                .remarks(request.getRemarks())
                .build();

        DelayLog savedLog = delayLogRepository.save(log);
        return mapToResponse(savedLog, area.getAreaName(), type.getTypeName(), type.getDelayGroup(), request.getLogDate(), request.getAreaId());
    }

    @Override
    public DelayLogResponse updateDelayLog(Long logId, DelayLogRequest request) {
        DelayLog log = delayLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("Delay Log not found for ID: " + logId));

        ShopArea area = shopAreaRepository.findById(request.getAreaId())
                .orElseThrow(() -> new ResourceNotFoundException("Shop Area not found for ID: " + request.getAreaId()));
        DelayType type = delayTypeRepository.findById(request.getDelayTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Delay Type not found for ID: " + request.getDelayTypeId()));

        ShiftOperationalLog opLog = shiftOperationalLogRepository
                .findByAreaIdAndLogDate(request.getAreaId(), request.getLogDate())
                .orElseThrow(() -> new IllegalArgumentException("No operational input found for this Area and Date. Please save operational inputs first."));

        int totalMinutes = calculateDuration(request.getStartTime(), request.getEndTime());
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;

        log.setOperationalLogId(opLog.getOperationalLogId());
        log.setDelayTypeId(request.getDelayTypeId());
        log.setReasonId(request.getReasonId());
        log.setDelayHours(hours);
        log.setDelayMinutes(mins);
        log.setStartTime(request.getStartTime());
        log.setEndTime(request.getEndTime());
        log.setShift(request.getShift());
        log.setDurationMinutes(totalMinutes);
        log.setRemarks(request.getRemarks());

        DelayLog savedLog = delayLogRepository.save(log);
        return mapToResponse(savedLog, area.getAreaName(), type.getTypeName(), type.getDelayGroup(), request.getLogDate(), request.getAreaId());
    }

    @Override
    @Transactional(readOnly = true)
    public DelayLogResponse getDelayLogById(Long logId) {
        DelayLog log = delayLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("Delay Log not found for ID: " + logId));
        
        ShiftOperationalLog opLog = shiftOperationalLogRepository.findById(log.getOperationalLogId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift Operational Log not found for ID: " + log.getOperationalLogId()));
        ShopArea area = shopAreaRepository.findById(opLog.getAreaId())
                .orElseThrow(() -> new ResourceNotFoundException("Shop Area not found for ID: " + opLog.getAreaId()));
        DelayType type = delayTypeRepository.findById(log.getDelayTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Delay Type not found for ID: " + log.getDelayTypeId()));

        return mapToResponse(log, area.getAreaName(), type.getTypeName(), type.getDelayGroup(), opLog.getLogDate(), opLog.getAreaId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DelayLogResponse> getDelayLogsByAreaAndDate(Long areaId, LocalDate date) {
        ShopArea area = shopAreaRepository.findById(areaId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop Area not found for ID: " + areaId));
                
        return shiftOperationalLogRepository.findByAreaIdAndLogDate(areaId, date)
                .map(opLog -> {
                    List<DelayLog> logs = delayLogRepository.findByOperationalLogId(opLog.getOperationalLogId());
                    return logs.stream().map(log -> {
                        DelayType type = delayTypeRepository.findById(log.getDelayTypeId())
                                .orElseThrow(() -> new ResourceNotFoundException("Delay Type not found for ID: " + log.getDelayTypeId()));
                        return mapToResponse(log, area.getAreaName(), type.getTypeName(), type.getDelayGroup(), date, areaId);
                    }).collect(Collectors.toList());
                })
                .orElseGet(java.util.Collections::emptyList);
    }

    @Override
    public void deleteDelayLog(Long logId) {
        if (!delayLogRepository.existsById(logId)) {
            throw new ResourceNotFoundException("Delay Log not found for ID: " + logId);
        }
        delayLogRepository.deleteById(logId);
    }

    private DelayLogResponse mapToResponse(DelayLog log, String areaName, String typeName, String delayGroup, LocalDate logDate, Long areaId) {
        String formattedDuration = String.format("%02d:%02d", log.getDelayHours(), log.getDelayMinutes());
        String reasonName = null;
        if (log.getReasonId() != null) {
            reasonName = delayReasonRepository.findById(log.getReasonId())
                    .map(DelayReason::getReasonName)
                    .orElse(null);
        }
        return DelayLogResponse.builder()
                .logId(log.getDelayLogId())
                .areaId(areaId)
                .areaName(areaName)
                .delayTypeId(log.getDelayTypeId())
                .typeName(typeName)
                .delayGroup(delayGroup)
                .reasonId(log.getReasonId())
                .reasonName(reasonName)
                .logDate(logDate)
                .delayHours(log.getDelayHours())
                .delayMinutes(log.getDelayMinutes())
                .remarks(log.getRemarks())
                .durationHHMM(formattedDuration)
                .startTime(log.getStartTime())
                .endTime(log.getEndTime())
                .shift(log.getShift())
                .durationMinutes(log.getDurationMinutes())
                .build();
    }
}
