package com.bsp.delay.service.impl;

import com.bsp.delay.entity.Shop;
import com.bsp.delay.entity.ShopArea;
import com.bsp.delay.repository.ShopAreaRepository;
import com.bsp.delay.repository.ShopRepository;
import com.bsp.delay.service.ShopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ShopServiceImpl implements ShopService {

    private final ShopRepository shopRepository;
    private final ShopAreaRepository shopAreaRepository;

    public ShopServiceImpl(ShopRepository shopRepository, ShopAreaRepository shopAreaRepository) {
        this.shopRepository = shopRepository;
        this.shopAreaRepository = shopAreaRepository;
    }

    @Override
    public List<Shop> getAllShops() {
        return shopRepository.findAll();
    }

    @Override
    public List<ShopArea> getAreasByShopId(Long shopId) {
        return shopAreaRepository.findByShopId(shopId);
    }

    @Override
    public List<ShopArea> getAllShopAreas() {
        return shopAreaRepository.findAll();
    }

    @Override
    public Shop getShopById(Long shopId) {
        return shopRepository.findById(shopId)
                .orElseThrow(() -> new com.bsp.delay.exception.ResourceNotFoundException("Shop not found for ID: " + shopId));
    }

    @Override
    public ShopArea getShopAreaById(Long areaId) {
        return shopAreaRepository.findById(areaId)
                .orElseThrow(() -> new com.bsp.delay.exception.ResourceNotFoundException("Shop Area not found for ID: " + areaId));
    }
}
