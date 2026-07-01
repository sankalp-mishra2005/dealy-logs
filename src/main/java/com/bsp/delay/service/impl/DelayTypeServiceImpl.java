package com.bsp.delay.service.impl;

import com.bsp.delay.entity.DelayType;
import com.bsp.delay.repository.DelayTypeRepository;
import com.bsp.delay.service.DelayTypeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DelayTypeServiceImpl implements DelayTypeService {

    private final DelayTypeRepository delayTypeRepository;

    public DelayTypeServiceImpl(DelayTypeRepository delayTypeRepository) {
        this.delayTypeRepository = delayTypeRepository;
    }

    @Override
    public List<DelayType> getAllDelayTypes() {
        return delayTypeRepository.findAll();
    }

    @Override
    public DelayType getDelayTypeById(Long typeId) {
        return delayTypeRepository.findById(typeId)
                .orElseThrow(() -> new com.bsp.delay.exception.ResourceNotFoundException("Delay Type not found for ID: " + typeId));
    }

}
