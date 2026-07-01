package com.bsp.delay.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResponse {
    private LocalDate reportDate;
    private Long shopId;
    private String shopName;
    private Map<String, AreaReport> areaReports;
    private ChartData charts;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChartData {
        private List<String> dailyTrendLabels;
        private List<Double> dailyTrendValues;
        private List<String> distributionLabels;
        private List<Double> distributionValues;
        private List<String> monthlyTrendLabels;
        private List<Double> monthlyTrendValues;
        private List<String> topDelaysLabels;
        private List<Double> topDelaysValues;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AreaReport {
        private Long areaId;
        private String areaName;
        private String areaCode;
        private Metrics day;
        private Metrics mtd;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Metrics {
        private String planned;
        private String electrical;
        private String mechanical;
        private String operation;
        private String sbs;
        private String fuelEmd;
        private String power;
        private String msds;
        private String others;
        private String totalDelay;
        private String controllable;
        private String nonControllable;
        private String availableHours;
        private String hotHours;
        private String repairHours;
        private String avgHotHours; // Format HH:MM (Month MTD only)
        private Double productionQty;
        private Double productionPerHour;
        private Double utilizationPct;
        private Double availabilityPct;
        private Double workingDays;
        private Double shifts;
    }
}
