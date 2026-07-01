package com.bsp.delay.service;

import com.bsp.delay.dto.DelayLogRequest;
import com.bsp.delay.dto.DelayLogResponse;
import java.time.LocalDate;
import java.util.List;

public interface DelayLogService {
    DelayLogResponse saveDelayLog(DelayLogRequest request);
    DelayLogResponse updateDelayLog(Long logId, DelayLogRequest request);
    DelayLogResponse getDelayLogById(Long logId);
    List<DelayLogResponse> getDelayLogsByAreaAndDate(Long areaId, LocalDate date);
    void deleteDelayLog(Long logId);
}
