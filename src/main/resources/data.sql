-- Seed Data for BSP Delay Management System (Oracle Compatible)

-- 1. Seed Shop Master
INSERT INTO SHOP (shop_id, shop_name, shop_code) VALUES (1, 'Plate Mill', 'PLATE_MILL');
INSERT INTO SHOP (shop_id, shop_name, shop_code) VALUES (2, 'Steel Melting Shop', 'SMS');

-- 2. Seed Shop Area Master
INSERT INTO SHOP_AREA (area_id, shop_id, area_name, area_code, sort_order) VALUES (1, 1, 'Mill Section', 'MILL', 10);
INSERT INTO SHOP_AREA (area_id, shop_id, area_name, area_code, sort_order) VALUES (2, 1, 'Normalizing Furnace', 'NORM', 20);
INSERT INTO SHOP_AREA (area_id, shop_id, area_name, area_code, sort_order) VALUES (3, 2, 'SMS-2', 'SMS_2', 10);
INSERT INTO SHOP_AREA (area_id, shop_id, area_name, area_code, sort_order) VALUES (4, 2, 'SMS-3', 'SMS_3', 20);

-- 3. Seed Delay Type Master
INSERT INTO DELAY_TYPE (delay_type_id, type_name, type_code, delay_group, sort_order) VALUES (1, 'Planned', 'PLANNED', 'P', 1);
INSERT INTO DELAY_TYPE (delay_type_id, type_name, type_code, delay_group, sort_order) VALUES (2, 'Electrical', 'ELEC', 'C', 2);
INSERT INTO DELAY_TYPE (delay_type_id, type_name, type_code, delay_group, sort_order) VALUES (3, 'Mechanical', 'MECH', 'C', 3);
INSERT INTO DELAY_TYPE (delay_type_id, type_name, type_code, delay_group, sort_order) VALUES (4, 'Operation', 'OPER', 'C', 4);
INSERT INTO DELAY_TYPE (delay_type_id, type_name, type_code, delay_group, sort_order) VALUES (5, 'SBS', 'SBS', 'NC', 5);
INSERT INTO DELAY_TYPE (delay_type_id, type_name, type_code, delay_group, sort_order) VALUES (6, 'Fuel/EMD', 'FUEL', 'NC', 6);
INSERT INTO DELAY_TYPE (delay_type_id, type_name, type_code, delay_group, sort_order) VALUES (7, 'Power', 'POWER', 'NC', 7);
INSERT INTO DELAY_TYPE (delay_type_id, type_name, type_code, delay_group, sort_order) VALUES (8, 'MSDS', 'MSDS', 'NC', 8);
INSERT INTO DELAY_TYPE (delay_type_id, type_name, type_code, delay_group, sort_order) VALUES (9, 'Others', 'OTHERS', 'NC', 9);
INSERT INTO DELAY_TYPE (delay_type_id, type_name, type_code, delay_group, sort_order) VALUES (10, 'Gas/Power Shortage', 'GAS_POWER_SHORTAGE', 'NC', 10);
INSERT INTO DELAY_TYPE (delay_type_id, type_name, type_code, delay_group, sort_order) VALUES (11, 'RM Shortage', 'RM_SHORTAGE', 'NC', 11);

-- 4. Seed Delay Reason Master
-- Planned (ID 1)
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (1, 2, 1, 'Conv Relining', 10);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (2, 2, 1, 'Conv S/D', 20);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (3, 2, 1, 'RH Vessel/Offtake Change', 30);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (4, 2, 1, 'LF S/D', 40);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (5, 2, 1, 'Caster S/D', 50);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (6, 2, 1, 'Gas Line Jobs', 60);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (7, 2, 1, 'Electrical Jobs', 70);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (8, 2, 1, 'Mechanical Jobs', 80);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (9, 2, 1, 'Crane S/D', 90);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (10, 2, 1, 'Shop S/D', 100);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (11, 2, 1, 'Conv Mid Campaign', 110);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (12, 2, 1, 'VAD S/D', 120);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (13, 2, 1, 'Mixer Relining', 130);

-- Operation (ID 4)
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (14, 2, 4, 'Ladle Failure / Purging Issues', 10);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (15, 2, 4, 'Tundish Failure', 20);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (16, 2, 4, 'BF Ladle Failure', 30);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (17, 2, 4, 'Slag Pot Availability Issues', 40);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (18, 2, 4, 'Heat Delay', 50);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (19, 2, 4, 'Breakout in Caster', 60);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (20, 2, 4, 'Strand Loss', 70);

-- Mechanical (ID 3)
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (21, 2, 3, 'Crane Down', 10);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (22, 2, 3, 'Hydraulic Failure', 20);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (23, 2, 3, 'Cooling Water Issues', 30);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (24, 2, 3, 'Equipment B/D', 40);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (25, 2, 3, 'Transfer Car Down', 50);

-- Electrical (ID 2)
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (26, 2, 2, 'PLC Failure', 10);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (27, 2, 2, 'Drive Failure', 20);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (28, 2, 2, 'Power Supply Failure', 30);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (29, 2, 2, 'Crane Down', 40);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (30, 2, 2, 'Cable Damage / Burnt', 50);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (31, 2, 2, 'Transfer Car Down', 60);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (32, 2, 2, 'Motor Damage', 70);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (33, 2, 2, 'Panel Problem', 80);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (34, 2, 2, 'Software Problem', 90);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (35, 2, 2, 'Motor Jam', 100);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (36, 2, 2, 'Sensor Problem', 110);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (37, 2, 2, 'Magnet Problem', 120);

-- Gas/Power Shortage (ID 10)
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (38, 2, 10, 'Low Coke Oven Gas Pressure', 10);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (39, 2, 10, 'Low Oxygen Flow / Pressure', 20);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (40, 2, 10, 'Low Argon Pressure', 30);

-- RM Shortage (ID 11)
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (41, 2, 11, 'Hot Metal Shortage', 10);
INSERT INTO DELAY_REASON (reason_id, shop_id, delay_type_id, reason_name, sort_order) VALUES (42, 2, 11, 'Other Raw Material Shortage', 20);

-- Commit transaction
COMMIT;

