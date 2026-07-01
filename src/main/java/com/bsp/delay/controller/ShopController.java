package com.bsp.delay.controller;

import com.bsp.delay.dto.ApiResponse;
import com.bsp.delay.entity.Shop;
import com.bsp.delay.entity.ShopArea;
import com.bsp.delay.service.ShopService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/api/shops")
    public ResponseEntity<ApiResponse<List<Shop>>> getAllShops() {
        List<Shop> shops = shopService.getAllShops();
        return ResponseEntity.ok(ApiResponse.success(shops, "Shops retrieved successfully"));
    }

    @GetMapping("/api/shops/{id}")
    public ResponseEntity<ApiResponse<Shop>> getShopById(@PathVariable Long id) {
        Shop shop = shopService.getShopById(id);
        return ResponseEntity.ok(ApiResponse.success(shop, "Shop retrieved successfully"));
    }

    @GetMapping("/api/shop-areas")
    public ResponseEntity<ApiResponse<List<ShopArea>>> getAllShopAreas() {
        List<ShopArea> areas = shopService.getAllShopAreas();
        return ResponseEntity.ok(ApiResponse.success(areas, "Shop areas retrieved successfully"));
    }

    @GetMapping("/api/shop-areas/{shopId}")
    public ResponseEntity<ApiResponse<List<ShopArea>>> getAreasByShopId(@PathVariable Long shopId) {
        List<ShopArea> areas = shopService.getAreasByShopId(shopId);
        return ResponseEntity.ok(ApiResponse.success(areas, "Shop areas retrieved successfully"));
    }

    @GetMapping("/api/shop-areas/detail/{id}")
    public ResponseEntity<ApiResponse<ShopArea>> getShopAreaById(@PathVariable Long id) {
        ShopArea area = shopService.getShopAreaById(id);
        return ResponseEntity.ok(ApiResponse.success(area, "Shop area retrieved successfully"));
    }
}
