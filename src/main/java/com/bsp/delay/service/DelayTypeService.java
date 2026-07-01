package com.bsp.delay.service;

import com.bsp.delay.entity.DelayType;
import java.util.List;

public interface DelayTypeService {
    List<DelayType> getAllDelayTypes();
    DelayType getDelayTypeById(Long typeId);
}
