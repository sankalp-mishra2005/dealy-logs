package com.bsp.delay.service;

import com.bsp.delay.entity.DelayReason;
import java.util.List;

public interface DelayReasonService {
    List<DelayReason> getDelayReasons(Long shopId, Long delayTypeId);
}
