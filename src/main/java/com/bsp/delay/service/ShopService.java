package com.bsp.delay.service;

import com.bsp.delay.entity.Shop;
import com.bsp.delay.entity.ShopArea;
import java.util.List;

public interface ShopService {
    List<Shop> getAllShops();
    List<ShopArea> getAreasByShopId(Long shopId);
    List<ShopArea> getAllShopAreas();
    Shop getShopById(Long shopId);
    ShopArea getShopAreaById(Long areaId);
}
