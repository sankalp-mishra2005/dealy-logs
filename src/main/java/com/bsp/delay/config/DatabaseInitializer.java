package com.bsp.delay.config;

import com.bsp.delay.entity.DelayType;
import com.bsp.delay.entity.Shop;
import com.bsp.delay.entity.ShopArea;
import com.bsp.delay.entity.DelayReason;
import com.bsp.delay.repository.DelayTypeRepository;
import com.bsp.delay.repository.ShopAreaRepository;
import com.bsp.delay.repository.ShopRepository;
import com.bsp.delay.repository.DelayReasonRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final ShopRepository shopRepository;
    private final ShopAreaRepository shopAreaRepository;
    private final DelayTypeRepository delayTypeRepository;
    private final DelayReasonRepository delayReasonRepository;

    public DatabaseInitializer(ShopRepository shopRepository,
                               ShopAreaRepository shopAreaRepository,
                               DelayTypeRepository delayTypeRepository,
                               DelayReasonRepository delayReasonRepository) {
        this.shopRepository = shopRepository;
        this.shopAreaRepository = shopAreaRepository;
        this.delayTypeRepository = delayTypeRepository;
        this.delayReasonRepository = delayReasonRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. Seed Shop Master
        if (!shopRepository.existsById(1L)) {
            Shop shop = Shop.builder().shopId(1L).shopName("Plate Mill").shopCode("PLATE_MILL").build();
            shopRepository.save(shop);
            System.out.println("Seeded Shop: Plate Mill");
        }
        if (!shopRepository.existsById(2L)) {
            Shop shop = Shop.builder().shopId(2L).shopName("Steel Melting Shop").shopCode("SMS").build();
            shopRepository.save(shop);
            System.out.println("Seeded Shop: Steel Melting Shop");
        }

        // 2. Seed Shop Area Master
        if (!shopAreaRepository.existsById(1L)) {
            ShopArea mill = ShopArea.builder().areaId(1L).shopId(1L).areaName("Mill Section").areaCode("MILL").sortOrder(10).build();
            shopAreaRepository.save(mill);
        }
        if (!shopAreaRepository.existsById(2L)) {
            ShopArea norm = ShopArea.builder().areaId(2L).shopId(1L).areaName("Normalizing Furnace").areaCode("NORM").sortOrder(20).build();
            shopAreaRepository.save(norm);
        }
        if (!shopAreaRepository.existsById(3L)) {
            ShopArea sms2 = ShopArea.builder().areaId(3L).shopId(2L).areaName("SMS-2").areaCode("SMS_2").sortOrder(10).build();
            shopAreaRepository.save(sms2);
        }
        if (!shopAreaRepository.existsById(4L)) {
            ShopArea sms3 = ShopArea.builder().areaId(4L).shopId(2L).areaName("SMS-3").areaCode("SMS_3").sortOrder(20).build();
            shopAreaRepository.save(sms3);
        }

        // 3. Seed Delay Type Master
        List<DelayType> expectedTypes = Arrays.asList(
            DelayType.builder().delayTypeId(1L).typeName("Planned").typeCode("PLANNED").delayGroup("P").sortOrder(1).build(),
            DelayType.builder().delayTypeId(2L).typeName("Electrical").typeCode("ELEC").delayGroup("C").sortOrder(2).build(),
            DelayType.builder().delayTypeId(3L).typeName("Mechanical").typeCode("MECH").delayGroup("C").sortOrder(3).build(),
            DelayType.builder().delayTypeId(4L).typeName("Operation").typeCode("OPER").delayGroup("C").sortOrder(4).build(),
            DelayType.builder().delayTypeId(5L).typeName("SBS").typeCode("SBS").delayGroup("NC").sortOrder(5).build(),
            DelayType.builder().delayTypeId(6L).typeName("Fuel/EMD").typeCode("FUEL").delayGroup("NC").sortOrder(6).build(),
            DelayType.builder().delayTypeId(7L).typeName("Power").typeCode("POWER").delayGroup("NC").sortOrder(7).build(),
            DelayType.builder().delayTypeId(8L).typeName("MSDS").typeCode("MSDS").delayGroup("NC").sortOrder(8).build(),
            DelayType.builder().delayTypeId(9L).typeName("Others").typeCode("OTHERS").delayGroup("NC").sortOrder(9).build(),
            DelayType.builder().delayTypeId(10L).typeName("Gas/Power Shortage").typeCode("GAS_POWER_SHORTAGE").delayGroup("NC").sortOrder(10).build(),
            DelayType.builder().delayTypeId(11L).typeName("RM Shortage").typeCode("RM_SHORTAGE").delayGroup("NC").sortOrder(11).build()
        );
        for (DelayType type : expectedTypes) {
            if (!delayTypeRepository.existsById(type.getDelayTypeId())) {
                delayTypeRepository.save(type);
                System.out.println("Seeded Delay Type: " + type.getTypeName());
            }
        }

        // 4. Seed Delay Reasons
        if (delayReasonRepository.count() == 0) {
            List<DelayReason> reasons = Arrays.asList(
                // Planned (ID 1)
                DelayReason.builder().reasonId(1L).shopId(2L).delayTypeId(1L).reasonName("Conv Relining").sortOrder(10).build(),
                DelayReason.builder().reasonId(2L).shopId(2L).delayTypeId(1L).reasonName("Conv S/D").sortOrder(20).build(),
                DelayReason.builder().reasonId(3L).shopId(2L).delayTypeId(1L).reasonName("RH Vessel/Offtake Change").sortOrder(30).build(),
                DelayReason.builder().reasonId(4L).shopId(2L).delayTypeId(1L).reasonName("LF S/D").sortOrder(40).build(),
                DelayReason.builder().reasonId(5L).shopId(2L).delayTypeId(1L).reasonName("Caster S/D").sortOrder(50).build(),
                DelayReason.builder().reasonId(6L).shopId(2L).delayTypeId(1L).reasonName("Gas Line Jobs").sortOrder(60).build(),
                DelayReason.builder().reasonId(7L).shopId(2L).delayTypeId(1L).reasonName("Electrical Jobs").sortOrder(70).build(),
                DelayReason.builder().reasonId(8L).shopId(2L).delayTypeId(1L).reasonName("Mechanical Jobs").sortOrder(80).build(),
                DelayReason.builder().reasonId(9L).shopId(2L).delayTypeId(1L).reasonName("Crane S/D").sortOrder(90).build(),
                DelayReason.builder().reasonId(10L).shopId(2L).delayTypeId(1L).reasonName("Shop S/D").sortOrder(100).build(),
                DelayReason.builder().reasonId(11L).shopId(2L).delayTypeId(1L).reasonName("Conv Mid Campaign").sortOrder(110).build(),
                DelayReason.builder().reasonId(12L).shopId(2L).delayTypeId(1L).reasonName("VAD S/D").sortOrder(120).build(),
                DelayReason.builder().reasonId(13L).shopId(2L).delayTypeId(1L).reasonName("Mixer Relining").sortOrder(130).build(),

                // Operation (ID 4)
                DelayReason.builder().reasonId(14L).shopId(2L).delayTypeId(4L).reasonName("Ladle Failure / Purging Issues").sortOrder(10).build(),
                DelayReason.builder().reasonId(15L).shopId(2L).delayTypeId(4L).reasonName("Tundish Failure").sortOrder(20).build(),
                DelayReason.builder().reasonId(16L).shopId(2L).delayTypeId(4L).reasonName("BF Ladle Failure").sortOrder(30).build(),
                DelayReason.builder().reasonId(17L).shopId(2L).delayTypeId(4L).reasonName("Slag Pot Availability Issues").sortOrder(40).build(),
                DelayReason.builder().reasonId(18L).shopId(2L).delayTypeId(4L).reasonName("Heat Delay").sortOrder(50).build(),
                DelayReason.builder().reasonId(19L).shopId(2L).delayTypeId(4L).reasonName("Breakout in Caster").sortOrder(60).build(),
                DelayReason.builder().reasonId(20L).shopId(2L).delayTypeId(4L).reasonName("Strand Loss").sortOrder(70).build(),

                // Mechanical (ID 3)
                DelayReason.builder().reasonId(21L).shopId(2L).delayTypeId(3L).reasonName("Crane Down").sortOrder(10).build(),
                DelayReason.builder().reasonId(22L).shopId(2L).delayTypeId(3L).reasonName("Hydraulic Failure").sortOrder(20).build(),
                DelayReason.builder().reasonId(23L).shopId(2L).delayTypeId(3L).reasonName("Cooling Water Issues").sortOrder(30).build(),
                DelayReason.builder().reasonId(24L).shopId(2L).delayTypeId(3L).reasonName("Equipment B/D").sortOrder(40).build(),
                DelayReason.builder().reasonId(25L).shopId(2L).delayTypeId(3L).reasonName("Transfer Car Down").sortOrder(50).build(),

                // Electrical (ID 2)
                DelayReason.builder().reasonId(26L).shopId(2L).delayTypeId(2L).reasonName("PLC Failure").sortOrder(10).build(),
                DelayReason.builder().reasonId(27L).shopId(2L).delayTypeId(2L).reasonName("Drive Failure").sortOrder(20).build(),
                DelayReason.builder().reasonId(28L).shopId(2L).delayTypeId(2L).reasonName("Power Supply Failure").sortOrder(30).build(),
                DelayReason.builder().reasonId(29L).shopId(2L).delayTypeId(2L).reasonName("Crane Down").sortOrder(40).build(),
                DelayReason.builder().reasonId(30L).shopId(2L).delayTypeId(2L).reasonName("Cable Damage / Burnt").sortOrder(50).build(),
                DelayReason.builder().reasonId(31L).shopId(2L).delayTypeId(2L).reasonName("Transfer Car Down").sortOrder(60).build(),
                DelayReason.builder().reasonId(32L).shopId(2L).delayTypeId(2L).reasonName("Motor Damage").sortOrder(70).build(),
                DelayReason.builder().reasonId(33L).shopId(2L).delayTypeId(2L).reasonName("Panel Problem").sortOrder(80).build(),
                DelayReason.builder().reasonId(34L).shopId(2L).delayTypeId(2L).reasonName("Software Problem").sortOrder(90).build(),
                DelayReason.builder().reasonId(35L).shopId(2L).delayTypeId(2L).reasonName("Motor Jam").sortOrder(100).build(),
                DelayReason.builder().reasonId(36L).shopId(2L).delayTypeId(2L).reasonName("Sensor Problem").sortOrder(110).build(),
                DelayReason.builder().reasonId(37L).shopId(2L).delayTypeId(2L).reasonName("Magnet Problem").sortOrder(120).build(),

                // Gas/Power Shortage (ID 10)
                DelayReason.builder().reasonId(38L).shopId(2L).delayTypeId(10L).reasonName("Low Coke Oven Gas Pressure").sortOrder(10).build(),
                DelayReason.builder().reasonId(39L).shopId(2L).delayTypeId(10L).reasonName("Low Oxygen Flow / Pressure").sortOrder(20).build(),
                DelayReason.builder().reasonId(40L).shopId(2L).delayTypeId(10L).reasonName("Low Argon Pressure").sortOrder(30).build(),

                // RM Shortage (ID 11)
                DelayReason.builder().reasonId(41L).shopId(2L).delayTypeId(11L).reasonName("Hot Metal Shortage").sortOrder(10).build(),
                DelayReason.builder().reasonId(42L).shopId(2L).delayTypeId(11L).reasonName("Other Raw Material Shortage").sortOrder(20).build()
            );
            delayReasonRepository.saveAll(reasons);
            System.out.println("Seeded Delay Reasons");
        }
    }
}
