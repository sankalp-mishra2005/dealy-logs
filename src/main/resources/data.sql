-- Seed Data for BSP Delay Management System (Oracle Compatible)

-- 1. Seed Shop Master
INSERT INTO SHOP (shop_id, shop_name, shop_code) VALUES (1, 'Plate Mill', 'PLATE_MILL');

-- 2. Seed Shop Area Master
INSERT INTO SHOP_AREA (area_id, shop_id, area_name, area_code, sort_order) VALUES (1, 1, 'Mill Section', 'MILL', 10);
INSERT INTO SHOP_AREA (area_id, shop_id, area_name, area_code, sort_order) VALUES (2, 1, 'Normalizing Furnace', 'NORM', 20);

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

-- Commit transaction
COMMIT;

