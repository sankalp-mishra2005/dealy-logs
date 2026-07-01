package com.bsp.delay.service.impl;

import com.bsp.delay.entity.DelayReason;
import com.bsp.delay.repository.DelayReasonRepository;
import com.bsp.delay.service.DelayReasonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DelayReasonServiceImpl implements DelayReasonService {

    private final DelayReasonRepository delayReasonRepository;

    public DelayReasonServiceImpl(DelayReasonRepository delayReasonRepository) {
        this.delayReasonRepository = delayReasonRepository;
    }

    @Override
    public List<DelayReason> getDelayReasons(Long shopId, Long delayTypeId) {
        return delayReasonRepository.findByShopIdAndDelayTypeIdOrderBySortOrderAsc(shopId, delayTypeId);
    }
}
