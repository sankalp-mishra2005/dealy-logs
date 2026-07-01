package com.bsp.delay.config;

import com.bsp.delay.entity.DelayType;
import com.bsp.delay.entity.Shop;
import com.bsp.delay.entity.ShopArea;
import com.bsp.delay.repository.DelayTypeRepository;
import com.bsp.delay.repository.ShopAreaRepository;
import com.bsp.delay.repository.ShopRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final ShopRepository shopRepository;
    private final ShopAreaRepository shopAreaRepository;
    private final DelayTypeRepository delayTypeRepository;

    public DatabaseInitializer(ShopRepository shopRepository,
                               ShopAreaRepository shopAreaRepository,
                               DelayTypeRepository delayTypeRepository) {
        this.shopRepository = shopRepository;
        this.shopAreaRepository = shopAreaRepository;
        this.delayTypeRepository = delayTypeRepository;
    }

    @Override
    public void run(String... args) throws Exception {


        // 1. Seed Shop Master
        if (shopRepository.count() == 0) {
            Shop shop = Shop.builder()
                    .shopId(1L)
                    .shopName("Plate Mill")
                    .shopCode("PLATE_MILL")
                    .build();
            shopRepository.save(shop);
            System.out.println("Seeded Shop: " + shop.getShopName());
        }

        // 2. Seed Shop Area Master
        if (shopAreaRepository.count() == 0) {
            ShopArea mill = ShopArea.builder()
                    .areaId(1L)
                    .shopId(1L)
                    .areaName("Mill Section")
                    .areaCode("MILL")
                    .sortOrder(10)
                    .build();
            ShopArea norm = ShopArea.builder()
                    .areaId(2L)
                    .shopId(1L)
                    .areaName("Normalizing Furnace")
                    .areaCode("NORM")
                    .sortOrder(20)
                    .build();
            shopAreaRepository.saveAll(Arrays.asList(mill, norm));
            System.out.println("Seeded Shop Areas: MILL, NORM");
        }

        // 3. Seed Delay Type Master
        if (delayTypeRepository.count() == 0) {
            List<DelayType> delayTypes = Arrays.asList(
                DelayType.builder().delayTypeId(1L).typeName("Planned").typeCode("PLANNED").delayGroup("P").sortOrder(1).build(),
                DelayType.builder().delayTypeId(2L).typeName("Electrical").typeCode("ELEC").delayGroup("C").sortOrder(2).build(),
                DelayType.builder().delayTypeId(3L).typeName("Mechanical").typeCode("MECH").delayGroup("C").sortOrder(3).build(),
                DelayType.builder().delayTypeId(4L).typeName("Operation").typeCode("OPER").delayGroup("C").sortOrder(4).build(),
                DelayType.builder().delayTypeId(5L).typeName("SBS").typeCode("SBS").delayGroup("NC").sortOrder(5).build(),
                DelayType.builder().delayTypeId(6L).typeName("Fuel/EMD").typeCode("FUEL").delayGroup("NC").sortOrder(6).build(),
                DelayType.builder().delayTypeId(7L).typeName("Power").typeCode("POWER").delayGroup("NC").sortOrder(7).build(),
                DelayType.builder().delayTypeId(8L).typeName("MSDS").typeCode("MSDS").delayGroup("NC").sortOrder(8).build(),
                DelayType.builder().delayTypeId(9L).typeName("Others").typeCode("OTHERS").delayGroup("NC").sortOrder(9).build()
            );
            delayTypeRepository.saveAll(delayTypes);
            System.out.println("Seeded Delay Types");
        }
    }
}
