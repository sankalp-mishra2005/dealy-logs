-- Oracle Database DDL Script for BSP Delay Management System (DELAY_MGMT Schema)

-- 1. SHOP Table
CREATE TABLE SHOP (
    shop_id NUMBER(19,0) PRIMARY KEY,
    shop_name VARCHAR2(100) NOT NULL,
    shop_code VARCHAR2(20) NOT NULL UNIQUE
);

CREATE SEQUENCE shop_seq START WITH 1 INCREMENT BY 1;

-- 2. SHOP_AREA Table
CREATE TABLE SHOP_AREA (
    area_id NUMBER(19,0) PRIMARY KEY,
    shop_id NUMBER(19,0) NOT NULL,
    area_name VARCHAR2(100) NOT NULL,
    area_code VARCHAR2(20) NOT NULL UNIQUE,
    sort_order NUMBER(10,0) DEFAULT 0,
    CONSTRAINT fk_area_shop FOREIGN KEY (shop_id) REFERENCES SHOP(shop_id)
);

CREATE SEQUENCE shop_area_seq START WITH 1 INCREMENT BY 1;

-- 3. DELAY_TYPE Table
CREATE TABLE DELAY_TYPE (
    delay_type_id NUMBER(19,0) PRIMARY KEY,
    type_name VARCHAR2(100) NOT NULL,
    type_code VARCHAR2(20) NOT NULL UNIQUE,
    delay_group VARCHAR2(10) NOT NULL, -- P, C, NC
    sort_order NUMBER(10,0) DEFAULT 0
);

CREATE SEQUENCE delay_type_seq START WITH 1 INCREMENT BY 1;

-- 3.5 DELAY_REASON Table
CREATE TABLE DELAY_REASON (
    reason_id NUMBER(19,0) PRIMARY KEY,
    shop_id NUMBER(19,0) NOT NULL,
    delay_type_id NUMBER(19,0) NOT NULL,
    reason_name VARCHAR2(255) NOT NULL,
    sort_order NUMBER(10,0) DEFAULT 0,
    CONSTRAINT fk_reason_shop FOREIGN KEY (shop_id) REFERENCES SHOP(shop_id),
    CONSTRAINT fk_reason_type FOREIGN KEY (delay_type_id) REFERENCES DELAY_TYPE(delay_type_id)
);

CREATE SEQUENCE delay_reason_seq START WITH 1 INCREMENT BY 1;

-- 4. SHIFT_OPERATIONAL_LOG Table
CREATE TABLE SHIFT_OPERATIONAL_LOG (
    operational_log_id NUMBER(19,0) PRIMARY KEY,
    report_date DATE NOT NULL,
    area_id NUMBER(19,0) NOT NULL,
    available_hours NUMBER(5,2) NOT NULL,
    production_tonnage NUMBER(19,2) NOT NULL,
    CONSTRAINT fk_op_log_area FOREIGN KEY (area_id) REFERENCES SHOP_AREA(area_id),
    CONSTRAINT uq_op_log_date_area UNIQUE (report_date, area_id)
);

CREATE SEQUENCE operational_log_seq START WITH 1 INCREMENT BY 1;

-- 5. DELAY_LOG Table
CREATE TABLE DELAY_LOG (
    delay_log_id NUMBER(19,0) PRIMARY KEY,
    operational_log_id NUMBER(19,0) NOT NULL,
    delay_type_id NUMBER(19,0) NOT NULL,
    reason_id NUMBER(19,0) NULL,
    delay_hours NUMBER(10,0) NOT NULL,
    delay_minutes NUMBER(10,0) NOT NULL,
    delay_reason VARCHAR2(4000) NOT NULL,
    start_time VARCHAR2(10),
    end_time VARCHAR2(10),
    shift VARCHAR2(50),
    duration_minutes NUMBER(10,0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_log_op_log FOREIGN KEY (operational_log_id) REFERENCES SHIFT_OPERATIONAL_LOG(operational_log_id) ON DELETE CASCADE,
    CONSTRAINT fk_log_type FOREIGN KEY (delay_type_id) REFERENCES DELAY_TYPE(delay_type_id),
    CONSTRAINT fk_log_reason FOREIGN KEY (reason_id) REFERENCES DELAY_REASON(reason_id)
);

CREATE SEQUENCE delay_log_seq START WITH 1 INCREMENT BY 1;

-- 6. Indexes for Performance Optimization
CREATE INDEX IDX_DELAY_LOG_OPERATIONAL ON DELAY_LOG(operational_log_id);
CREATE INDEX IDX_SHIFT_DATE_AREA ON SHIFT_OPERATIONAL_LOG(report_date, area_id);
CREATE INDEX IDX_DELAY_TYPE_CODE ON DELAY_TYPE(type_code);

