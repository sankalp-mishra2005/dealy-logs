package com.bsp.delay.service.impl;

import com.bsp.delay.dto.ReportResponse;
import com.bsp.delay.dto.DelayLogResponse;
import com.bsp.delay.entity.*;
import com.bsp.delay.exception.ResourceNotFoundException;
import com.bsp.delay.repository.*;
import com.bsp.delay.service.ReportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.FontFactory;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.BaseFont;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final ShopRepository shopRepository;
    private final ShopAreaRepository shopAreaRepository;
    private final DelayLogRepository delayLogRepository;
    private final DelayTypeRepository delayTypeRepository;
    private final ShiftOperationalLogRepository shiftOperationalLogRepository;

    public ReportServiceImpl(ShopRepository shopRepository,
                             ShopAreaRepository shopAreaRepository,
                             DelayLogRepository delayLogRepository,
                             DelayTypeRepository delayTypeRepository,
                             ShiftOperationalLogRepository shiftOperationalLogRepository) {
        this.shopRepository = shopRepository;
        this.shopAreaRepository = shopAreaRepository;
        this.delayLogRepository = delayLogRepository;
        this.delayTypeRepository = delayTypeRepository;
        this.shiftOperationalLogRepository = shiftOperationalLogRepository;
    }

    @Override
    public ReportResponse generateReport(Long shopId, Long areaId, String reportType, LocalDate date, String month, Integer year, LocalDate fromDate, LocalDate toDate, Long delayTypeId, String shift) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with ID: " + shopId));

        // 1. Resolve date range based on reportType
        LocalDate startDate;
        LocalDate endDate;
        LocalDate activeDate = date != null ? date : LocalDate.now();

        if ("DAILY".equalsIgnoreCase(reportType)) {
            startDate = activeDate.withDayOfMonth(1); // month-to-date up to active date
            endDate = activeDate;
        } else if ("MONTHLY".equalsIgnoreCase(reportType)) {
            if (month != null && !month.trim().isEmpty()) {
                YearMonth ym = YearMonth.parse(month);
                startDate = ym.atDay(1);
                endDate = ym.atEndOfMonth();
            } else {
                startDate = activeDate.withDayOfMonth(1);
                endDate = activeDate.withDayOfMonth(activeDate.lengthOfMonth());
            }
            activeDate = endDate; // use end of month as active reference day
        } else if ("YEARLY".equalsIgnoreCase(reportType)) {
            int targetYear = year != null ? year : activeDate.getYear();
            // Indian Financial Year: April 1st to March 31st
            startDate = LocalDate.of(targetYear, 4, 1);
            endDate = LocalDate.of(targetYear + 1, 3, 31);
            activeDate = endDate;
        } else if ("CUSTOM".equalsIgnoreCase(reportType)) {
            startDate = fromDate != null ? fromDate : activeDate.minusDays(30);
            endDate = toDate != null ? toDate : activeDate;
            activeDate = endDate;
        } else { // default to monthly current
            startDate = activeDate.withDayOfMonth(1);
            endDate = activeDate;
        }

        // 2. Fetch reference areas
        List<ShopArea> areas = shopAreaRepository.findByShopId(shopId);
        if (areaId != null) {
            areas = areas.stream()
                    .filter(a -> a.getAreaId().equals(areaId))
                    .collect(Collectors.toList());
            if (areas.isEmpty()) {
                throw new ResourceNotFoundException("Shop Area not found for ID: " + areaId + " under Shop: " + shopId);
            }
        }

        // Cache Delay Types
        List<DelayType> delayTypes = delayTypeRepository.findAll();
        Map<Long, DelayType> delayTypeMap = delayTypes.stream()
                .collect(Collectors.toMap(DelayType::getDelayTypeId, dt -> dt));

        // 3. Query filtered databases dynamically
        List<DelayLog> periodDelays = delayLogRepository.findFilteredDelays(shopId, startDate, endDate, areaId, (shift == null || "All".equalsIgnoreCase(shift)) ? null : shift, delayTypeId);
        List<ShiftOperationalLog> periodOpLogs = shiftOperationalLogRepository.findFilteredOperationalLogs(shopId, startDate, endDate, areaId);

        // Group by Area
        Map<Long, List<DelayLog>> delaysByArea = periodDelays.stream()
                .collect(Collectors.groupingBy(log -> {
                    // Find operational log to get areaId
                    return periodOpLogs.stream()
                            .filter(sol -> sol.getOperationalLogId().equals(log.getOperationalLogId()))
                            .map(ShiftOperationalLog::getAreaId)
                            .findFirst()
                            .orElse(0L);
                }));

        Map<Long, List<ShiftOperationalLog>> opLogsByArea = periodOpLogs.stream()
                .collect(Collectors.groupingBy(ShiftOperationalLog::getAreaId));

        Map<String, ReportResponse.AreaReport> areaReportsMap = new HashMap<>();

        for (ShopArea area : areas) {
            List<DelayLog> areaDelays = delaysByArea.getOrDefault(area.getAreaId(), Collections.emptyList());
            List<ShiftOperationalLog> areaOpLogs = opLogsByArea.getOrDefault(area.getAreaId(), Collections.emptyList());

            ReportResponse.AreaReport areaReport = generateAreaReportDynamic(area, activeDate, startDate, endDate, areaDelays, areaOpLogs, delayTypeMap, shift);
            areaReportsMap.put(area.getAreaCode(), areaReport);
        }

        // 4. Generate dynamic chart configurations
        ReportResponse.ChartData charts = buildChartData(periodDelays, delayTypeMap, startDate, endDate);

        return ReportResponse.builder()
                .reportDate(activeDate)
                .shopId(shopId)
                .shopName(shop.getShopName())
                .reportType(shop.getShopCode())
                .areaReports(areaReportsMap)
                .charts(charts)
                .build();
    }

    private static class DelayCalculationResult {
        double planned = 0.0;
        double controllable = 0.0;
        double nonControllable = 0.0;
        Map<String, Double> typeSums = new HashMap<>();
    }

    private DelayCalculationResult calculateDelaysGeneric(List<DelayLog> logs, Collection<DelayType> allTypes) {
        DelayCalculationResult result = new DelayCalculationResult();
        Map<Long, DelayType> typeMap = allTypes.stream().collect(Collectors.toMap(DelayType::getDelayTypeId, t -> t));
        for (DelayLog log : logs) {
            DelayType type = typeMap.get(log.getDelayTypeId());
            if (type != null) {
                double duration = log.getDelayHours() + (log.getDelayMinutes() / 60.0);
                String code = type.getTypeCode().toUpperCase();
                result.typeSums.put(code, result.typeSums.getOrDefault(code, 0.0) + duration);
                
                String grp = type.getDelayGroup().toUpperCase();
                if ("P".equals(grp)) {
                    result.planned += duration;
                } else if ("C".equals(grp)) {
                    result.controllable += duration;
                } else if ("NC".equals(grp)) {
                    result.nonControllable += duration;
                }
            }
        }
        return result;
    }

    private ReportResponse.AreaReport generateAreaReportDynamic(ShopArea area, LocalDate activeDate, LocalDate startDate, LocalDate endDate,
                                                                 List<DelayLog> areaDelays, List<ShiftOperationalLog> areaOpLogs,
                                                                 Map<Long, DelayType> delayTypeMap, String shift) {

        int calendarDays = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        boolean isShiftFiltered = (shift != null && !shift.trim().isEmpty() && !"All".equalsIgnoreCase(shift));

        // ---- A. Calculate Active Day Metrics ----
        List<DelayLog> dayDelays = areaDelays.stream()
                .filter(d -> {
                    return areaOpLogs.stream()
                            .filter(op -> op.getOperationalLogId().equals(d.getOperationalLogId()))
                            .anyMatch(op -> op.getLogDate().equals(activeDate));
                })
                .collect(Collectors.toList());

        Optional<ShiftOperationalLog> dayInputOpt = areaOpLogs.stream()
                .filter(op -> op.getLogDate().equals(activeDate))
                .findFirst();

        double dayAvailHours = isShiftFiltered ? 8.0 : 24.0;
        double dayProductionQty = 0.0;
        double dayWorkingDays = isShiftFiltered ? 0.33 : 1.0;
        double dayShifts = isShiftFiltered ? 1.0 : 3.0;

        if (dayInputOpt.isPresent()) {
            ShiftOperationalLog dayInput = dayInputOpt.get();
            dayAvailHours = isShiftFiltered ? 8.0 : dayInput.getAvailableHours();
            dayProductionQty = dayInput.getProductionTonnage();
        } else {
            // Apply standard fallbacks
            if ("MILL".equalsIgnoreCase(area.getAreaCode())) {
                dayProductionQty = 4000.0;
            } else if ("NORM".equalsIgnoreCase(area.getAreaCode())) {
                dayProductionQty = 600.0;
            } else if ("SMS_2".equalsIgnoreCase(area.getAreaCode())) {
                dayProductionQty = 3000.0;
            } else if ("SMS_3".equalsIgnoreCase(area.getAreaCode())) {
                dayProductionQty = 3500.0;
            } else {
                dayProductionQty = 1000.0;
            }
        }

        DelayCalculationResult dayCalc = calculateDelaysGeneric(dayDelays, delayTypeMap.values());
        ReportResponse.Metrics dayMetrics = compileMetrics(dayAvailHours, dayProductionQty, 0.0, dayWorkingDays, dayShifts, dayCalc, delayTypeMap.values(), 1, 0.0);

        // ---- B. Calculate Period Cumulative (MTD/YTD/Range) Metrics ----
        Map<LocalDate, ShiftOperationalLog> opLogMap = areaOpLogs.stream()
                .collect(Collectors.toMap(ShiftOperationalLog::getLogDate, op -> op, (first, second) -> first));

        double periodAvailHours = 0.0;
        double periodProductionQty = 0.0;
        double periodWorkingDays = isShiftFiltered ? ((double) calendarDays / 3.0) : (double) calendarDays;
        double periodShifts = (double) calendarDays * (isShiftFiltered ? 1.0 : 3.0);

        for (int d = 1; d <= calendarDays; d++) {
            LocalDate currentLocalDate = startDate.plusDays(d - 1);
            ShiftOperationalLog input = opLogMap.get(currentLocalDate);
            if (input != null) {
                periodAvailHours += (isShiftFiltered ? 8.0 : input.getAvailableHours());
                periodProductionQty += input.getProductionTonnage();
            } else {
                // Fallback defaults
                periodAvailHours += (isShiftFiltered ? 8.0 : 24.0);
                if ("MILL".equalsIgnoreCase(area.getAreaCode())) {
                    periodProductionQty += 4000.0;
                } else if ("NORM".equalsIgnoreCase(area.getAreaCode())) {
                    periodProductionQty += 600.0;
                } else if ("SMS_2".equalsIgnoreCase(area.getAreaCode())) {
                    periodProductionQty += 3000.0;
                } else if ("SMS_3".equalsIgnoreCase(area.getAreaCode())) {
                    periodProductionQty += 3500.0;
                } else {
                    periodProductionQty += 1000.0;
                }
            }
        }

        DelayCalculationResult periodCalc = calculateDelaysGeneric(areaDelays, delayTypeMap.values());
        double periodTotalDelay = periodCalc.planned + periodCalc.controllable + periodCalc.nonControllable;
        double periodHotHours = Math.max(0.0, periodAvailHours - periodTotalDelay);

        ReportResponse.Metrics periodMetrics = compileMetrics(periodAvailHours, periodProductionQty, 0.0, periodWorkingDays, periodShifts, periodCalc, delayTypeMap.values(), calendarDays, periodHotHours);

        return ReportResponse.AreaReport.builder()
                .areaId(area.getAreaId())
                .areaName(area.getAreaName())
                .areaCode(area.getAreaCode())
                .day(dayMetrics)
                .mtd(periodMetrics)
                .build();
    }

    private double calculateAvailability(double availableHours, double targetCalendarHours) {
        if (targetCalendarHours <= 0) return 0.0;
        return (availableHours / targetCalendarHours) * 100.0;
    }

    private ReportResponse.Metrics compileMetrics(double availHours, double productionQty, double repairHours,
                                                  double workingDays, double shifts,
                                                  DelayCalculationResult calc, Collection<DelayType> allTypes,
                                                  int calendarDays, double totalPeriodHotHours) {
        double planned = calc.planned;
        double controllable = calc.controllable;
        double nonControllable = calc.nonControllable;
        double totalDelay = planned + controllable + nonControllable;
        double hotHours = Math.max(0.0, availHours - totalDelay);

        double prodPerHour = hotHours > 0 ? (productionQty / hotHours) : 0.0;
        double utilizationPct = availHours > 0 ? (hotHours / availHours) * 100.0 : 0.0;

        double targetCalendarHours = calendarDays * 24.0;
        double availabilityPct = calculateAvailability(availHours, targetCalendarHours);

        String avgHotHoursStr = "";
        if (calendarDays > 1) {
            double avgHotHours = workingDays > 0 ? (totalPeriodHotHours / workingDays) : 0.0;
            avgHotHoursStr = formatHoursToHHMM(avgHotHours);
        }

        Map<String, String> delayTypeDurations = new HashMap<>();
        for (DelayType dt : allTypes) {
            delayTypeDurations.put(dt.getTypeCode().toUpperCase(), "00:00");
        }
        for (Map.Entry<String, Double> entry : calc.typeSums.entrySet()) {
            delayTypeDurations.put(entry.getKey(), formatHoursToHHMM(entry.getValue()));
        }

        return ReportResponse.Metrics.builder()
                .delayTypeDurations(delayTypeDurations)
                .totalDelay(formatHoursToHHMM(totalDelay))
                .controllable(formatHoursToHHMM(controllable))
                .nonControllable(formatHoursToHHMM(nonControllable))
                .availableHours(formatHoursToHHMM(availHours))
                .hotHours(formatHoursToHHMM(hotHours))
                .repairHours(formatHoursToHHMM(repairHours))
                .avgHotHours(avgHotHoursStr)
                .productionQty(productionQty)
                .productionPerHour(Math.round(prodPerHour * 100.0) / 100.0)
                .utilizationPct(Math.round(utilizationPct * 100.0) / 100.0)
                .availabilityPct(Math.round(availabilityPct * 100.0) / 100.0)
                .workingDays(Math.round(workingDays * 100.0) / 100.0)
                .shifts(Math.round(shifts * 100.0) / 100.0)
                .build();
    }

    private String formatHoursToHHMM(double decimalHours) {
        if (Double.isNaN(decimalHours) || decimalHours <= 0) {
            return "00:00";
        }
        long totalMinutes = Math.round(decimalHours * 60.0);
        long hrs = totalMinutes / 60;
        long mins = totalMinutes % 60;
        return String.format("%02d:%02d", hrs, mins);
    }

    private ReportResponse.ChartData buildChartData(List<DelayLog> delays, Map<Long, DelayType> delayTypeMap, LocalDate startDate, LocalDate endDate) {
        // 1. Daily Trend
        Map<LocalDate, Double> dailySum = new TreeMap<>();
        // Initialize all dates in range with 0.0
        LocalDate temp = startDate;
        while (!temp.isAfter(endDate)) {
            dailySum.put(temp, 0.0);
            temp = temp.plusDays(1);
        }

        // Sum delays per day
        for (DelayLog log : delays) {
            double hrs = log.getDelayHours() + (log.getDelayMinutes() / 60.0);
            // Locate date via ShiftOperationalLog (mock date if not loaded)
            LocalDate logDate = startDate; // fallback
            Optional<ShiftOperationalLog> solOpt = shiftOperationalLogRepository.findById(log.getOperationalLogId());
            if (solOpt.isPresent()) {
                logDate = solOpt.get().getLogDate();
            }
            if (dailySum.containsKey(logDate)) {
                dailySum.put(logDate, dailySum.get(logDate) + hrs);
            }
        }

        List<String> dailyTrendLabels = new ArrayList<>();
        List<Double> dailyTrendValues = new ArrayList<>();
        // Limit to last 30 entries if range is huge
        int totalDays = dailySum.size();
        int count = 0;
        for (Map.Entry<LocalDate, Double> entry : dailySum.entrySet()) {
            if (totalDays <= 31 || count >= (totalDays - 30)) {
                dailyTrendLabels.add(entry.getKey().toString());
                dailyTrendValues.add(Math.round(entry.getValue() * 10.0) / 10.0);
            }
            count++;
        }

        // 2. Delay Type Distribution
        Map<String, Double> typeSum = new HashMap<>();
        for (DelayLog log : delays) {
            DelayType dt = delayTypeMap.get(log.getDelayTypeId());
            if (dt != null) {
                double hrs = log.getDelayHours() + (log.getDelayMinutes() / 60.0);
                typeSum.put(dt.getTypeName(), typeSum.getOrDefault(dt.getTypeName(), 0.0) + hrs);
            }
        }
        List<String> distributionLabels = new ArrayList<>(typeSum.keySet());
        List<Double> distributionValues = distributionLabels.stream()
                .map(lbl -> Math.round(typeSum.get(lbl) * 10.0) / 10.0)
                .collect(Collectors.toList());

        // 3. Monthly Trend (grouping by month strings "MMM yyyy")
        Map<String, Double> monthlySum = new LinkedHashMap<>();
        LocalDate monthIter = startDate;
        while (!monthIter.isAfter(endDate)) {
            String label = monthIter.format(DateTimeFormatter.ofPattern("MMM yyyy"));
            monthlySum.putIfAbsent(label, 0.0);
            monthIter = monthIter.plusDays(1);
        }

        for (DelayLog log : delays) {
            double hrs = log.getDelayHours() + (log.getDelayMinutes() / 60.0);
            Optional<ShiftOperationalLog> solOpt = shiftOperationalLogRepository.findById(log.getOperationalLogId());
            if (solOpt.isPresent()) {
                String label = solOpt.get().getLogDate().format(DateTimeFormatter.ofPattern("MMM yyyy"));
                if (monthlySum.containsKey(label)) {
                    monthlySum.put(label, monthlySum.get(label) + hrs);
                }
            }
        }
        List<String> monthlyTrendLabels = new ArrayList<>(monthlySum.keySet());
        List<Double> monthlyTrendValues = monthlyTrendLabels.stream()
                .map(lbl -> Math.round(monthlySum.get(lbl) * 10.0) / 10.0)
                .collect(Collectors.toList());

        // 4. Top Delay Types (sorted bar values)
        List<Map.Entry<String, Double>> sortedTypes = typeSum.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .collect(Collectors.toList());

        List<String> topDelaysLabels = sortedTypes.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        List<Double> topDelaysValues = sortedTypes.stream().map(entry -> Math.round(entry.getValue() * 10.0) / 10.0).collect(Collectors.toList());

        return ReportResponse.ChartData.builder()
                .dailyTrendLabels(dailyTrendLabels)
                .dailyTrendValues(dailyTrendValues)
                .distributionLabels(distributionLabels)
                .distributionValues(distributionValues)
                .monthlyTrendLabels(monthlyTrendLabels)
                .monthlyTrendValues(monthlyTrendValues)
                .topDelaysLabels(topDelaysLabels)
                .topDelaysValues(topDelaysValues)
                .build();
    }

    @Override
    public byte[] generateExcelReport(Long shopId, Long areaId, String reportType, LocalDate date, String month, Integer year, LocalDate fromDate, LocalDate toDate, Long delayTypeId, String shift) throws IOException {
        ReportResponse report = generateReport(shopId, areaId, reportType, date, month, year, fromDate, toDate, delayTypeId, shift);
        
        if (shopId == 2) {
            ReportResponse.AreaReport sms2 = report.getAreaReports() != null ? report.getAreaReports().get("SMS_2") : null;
            ReportResponse.AreaReport sms3 = report.getAreaReports() != null ? report.getAreaReports().get("SMS_3") : null;
            
            String[][] rows = {
                {"Planned", getDelayDuration(sms2, "day", "PLANNED"), getDelayDuration(sms2, "mtd", "PLANNED"), getDelayDuration(sms3, "day", "PLANNED"), getDelayDuration(sms3, "mtd", "PLANNED")},
                {"Electrical", getDelayDuration(sms2, "day", "ELEC"), getDelayDuration(sms2, "mtd", "ELEC"), getDelayDuration(sms3, "day", "ELEC"), getDelayDuration(sms3, "mtd", "ELEC")},
                {"Mechanical", getDelayDuration(sms2, "day", "MECH"), getDelayDuration(sms2, "mtd", "MECH"), getDelayDuration(sms3, "day", "MECH"), getDelayDuration(sms3, "mtd", "MECH")},
                {"Operation", getDelayDuration(sms2, "day", "OPER"), getDelayDuration(sms2, "mtd", "OPER"), getDelayDuration(sms3, "day", "OPER"), getDelayDuration(sms3, "mtd", "OPER")},
                {"SBS", getDelayDuration(sms2, "day", "SBS"), getDelayDuration(sms2, "mtd", "SBS"), getDelayDuration(sms3, "day", "SBS"), getDelayDuration(sms3, "mtd", "SBS")},
                {"Fuel/EMD", getDelayDuration(sms2, "day", "FUEL"), getDelayDuration(sms2, "mtd", "FUEL"), getDelayDuration(sms3, "day", "FUEL"), getDelayDuration(sms3, "mtd", "FUEL")},
                {"Power", getDelayDuration(sms2, "day", "POWER"), getDelayDuration(sms2, "mtd", "POWER"), getDelayDuration(sms3, "day", "POWER"), getDelayDuration(sms3, "mtd", "POWER")},
                {"MSDS", getDelayDuration(sms2, "day", "MSDS"), getDelayDuration(sms2, "mtd", "MSDS"), getDelayDuration(sms3, "day", "MSDS"), getDelayDuration(sms3, "mtd", "MSDS")},
                {"Gas/Power Shortage", getDelayDuration(sms2, "day", "GAS_POWER_SHORTAGE"), getDelayDuration(sms2, "mtd", "GAS_POWER_SHORTAGE"), getDelayDuration(sms3, "day", "GAS_POWER_SHORTAGE"), getDelayDuration(sms3, "mtd", "GAS_POWER_SHORTAGE")},
                {"RM Shortage", getDelayDuration(sms2, "day", "RM_SHORTAGE"), getDelayDuration(sms2, "mtd", "RM_SHORTAGE"), getDelayDuration(sms3, "day", "RM_SHORTAGE"), getDelayDuration(sms3, "mtd", "RM_SHORTAGE")},
                {"Others", getDelayDuration(sms2, "day", "OTHERS"), getDelayDuration(sms2, "mtd", "OTHERS"), getDelayDuration(sms3, "day", "OTHERS"), getDelayDuration(sms3, "mtd", "OTHERS")},
                {"Total Delay", sms2 != null ? sms2.getDay().getTotalDelay() : "", sms2 != null ? sms2.getMtd().getTotalDelay() : "", sms3 != null ? sms3.getDay().getTotalDelay() : "", sms3 != null ? sms3.getMtd().getTotalDelay() : ""},
                {"Controllable", sms2 != null ? sms2.getDay().getControllable() : "", sms2 != null ? sms2.getMtd().getControllable() : "", sms3 != null ? sms3.getDay().getControllable() : "", sms3 != null ? sms3.getMtd().getControllable() : ""},
                {"Non-Controllable", sms2 != null ? sms2.getDay().getNonControllable() : "", sms2 != null ? sms2.getMtd().getNonControllable() : "", sms3 != null ? sms3.getDay().getNonControllable() : "", sms3 != null ? sms3.getMtd().getNonControllable() : ""},
                {"Available Hours", sms2 != null ? sms2.getDay().getAvailableHours() : "", sms2 != null ? sms2.getMtd().getAvailableHours() : "", sms3 != null ? sms3.getDay().getAvailableHours() : "", sms3 != null ? sms3.getMtd().getAvailableHours() : ""},
                {"Hot Hours", sms2 != null ? sms2.getDay().getHotHours() : "", sms2 != null ? sms2.getMtd().getHotHours() : "", sms3 != null ? sms3.getDay().getHotHours() : "", sms3 != null ? sms3.getMtd().getHotHours() : ""},
                {"Production/HR (T/H)", sms2 != null ? String.valueOf(sms2.getDay().getProductionPerHour()) : "", sms2 != null ? String.valueOf(sms2.getMtd().getProductionPerHour()) : "", sms3 != null ? String.valueOf(sms3.getDay().getProductionPerHour()) : "", sms3 != null ? String.valueOf(sms3.getMtd().getProductionPerHour()) : ""},
                {"Utilization %", sms2 != null ? String.format("%.2f%%", sms2.getDay().getUtilizationPct()) : "", sms2 != null ? String.format("%.2f%%", sms2.getMtd().getUtilizationPct()) : "", sms3 != null ? String.format("%.2f%%", sms3.getDay().getUtilizationPct()) : "", sms3 != null ? String.format("%.2f%%", sms3.getMtd().getUtilizationPct()) : ""},
                {"Availability %", sms2 != null ? String.format("%.2f%%", sms2.getDay().getAvailabilityPct()) : "", sms2 != null ? String.format("%.2f%%", sms2.getMtd().getAvailabilityPct()) : "", sms3 != null ? String.format("%.2f%%", sms3.getDay().getAvailabilityPct()) : "", sms3 != null ? String.format("%.2f%%", sms3.getMtd().getAvailabilityPct()) : ""},
                {"Avg Hot Hours (H/Day)", "—", sms2 != null ? sms2.getMtd().getAvgHotHours() : "", "—", sms3 != null ? sms3.getMtd().getAvgHotHours() : ""},
                {"Working Days", sms2 != null ? String.valueOf(sms2.getDay().getWorkingDays()) : "", sms2 != null ? String.valueOf(sms2.getMtd().getWorkingDays()) : "", sms3 != null ? String.valueOf(sms3.getDay().getWorkingDays()) : "", sms3 != null ? String.valueOf(sms3.getMtd().getWorkingDays()) : ""},
                {"No Of Work Shift", sms2 != null ? String.valueOf(sms2.getDay().getShifts()) : "", sms2 != null ? String.valueOf(sms2.getMtd().getShifts()) : "", sms3 != null ? String.valueOf(sms3.getDay().getShifts()) : "", sms3 != null ? String.valueOf(sms3.getMtd().getShifts()) : ""},
            };
            Set<Integer> totals = new HashSet<>(Arrays.asList(11, 12, 13));
            return generateGenericExcel(report, "Steel Melting Shop Production Division - Daily Production Report (DPR)", "SMS_2", "SMS-2", "SMS_3", "SMS-3", rows, totals, reportType, date, month, year, fromDate, toDate);
        } else {
            ReportResponse.AreaReport mill = report.getAreaReports() != null ? report.getAreaReports().get("MILL") : null;
            ReportResponse.AreaReport norm = report.getAreaReports() != null ? report.getAreaReports().get("NORM") : null;
            
            String[][] rows = {
                {"Planned", getDelayDuration(mill, "day", "PLANNED"), getDelayDuration(mill, "mtd", "PLANNED"), getDelayDuration(norm, "day", "PLANNED"), getDelayDuration(norm, "mtd", "PLANNED")},
                {"Electrical", getDelayDuration(mill, "day", "ELEC"), getDelayDuration(mill, "mtd", "ELEC"), getDelayDuration(norm, "day", "ELEC"), getDelayDuration(norm, "mtd", "ELEC")},
                {"Mechanical", getDelayDuration(mill, "day", "MECH"), getDelayDuration(mill, "mtd", "MECH"), getDelayDuration(norm, "day", "MECH"), getDelayDuration(norm, "mtd", "MECH")},
                {"Operation", getDelayDuration(mill, "day", "OPER"), getDelayDuration(mill, "mtd", "OPER"), getDelayDuration(norm, "day", "OPER"), getDelayDuration(norm, "mtd", "OPER")},
                {"SBS", getDelayDuration(mill, "day", "SBS"), getDelayDuration(mill, "mtd", "SBS"), getDelayDuration(norm, "day", "SBS"), getDelayDuration(norm, "mtd", "SBS")},
                {"Fuel/EMD", getDelayDuration(mill, "day", "FUEL"), getDelayDuration(mill, "mtd", "FUEL"), getDelayDuration(norm, "day", "FUEL"), getDelayDuration(norm, "mtd", "FUEL")},
                {"Power", getDelayDuration(mill, "day", "POWER"), getDelayDuration(mill, "mtd", "POWER"), getDelayDuration(norm, "day", "POWER"), getDelayDuration(norm, "mtd", "POWER")},
                {"MSDS", getDelayDuration(mill, "day", "MSDS"), getDelayDuration(mill, "mtd", "MSDS"), getDelayDuration(norm, "day", "MSDS"), getDelayDuration(norm, "mtd", "MSDS")},
                {"Others", getDelayDuration(mill, "day", "OTHERS"), getDelayDuration(mill, "mtd", "OTHERS"), getDelayDuration(norm, "day", "OTHERS"), getDelayDuration(norm, "mtd", "OTHERS")},
                {"Total Delay", mill != null ? mill.getDay().getTotalDelay() : "", mill != null ? mill.getMtd().getTotalDelay() : "", norm != null ? norm.getDay().getTotalDelay() : "", norm != null ? norm.getMtd().getTotalDelay() : ""},
                {"Controllable", mill != null ? mill.getDay().getControllable() : "", mill != null ? mill.getMtd().getControllable() : "", norm != null ? norm.getDay().getControllable() : "", norm != null ? norm.getMtd().getControllable() : ""},
                {"Non-Controllable", mill != null ? mill.getDay().getNonControllable() : "", mill != null ? mill.getMtd().getNonControllable() : "", norm != null ? norm.getDay().getNonControllable() : "", norm != null ? norm.getMtd().getNonControllable() : ""},
                {"Available Hours", mill != null ? mill.getDay().getAvailableHours() : "", mill != null ? mill.getMtd().getAvailableHours() : "", norm != null ? norm.getDay().getAvailableHours() : "", norm != null ? norm.getMtd().getAvailableHours() : ""},
                {"Hot Hours", mill != null ? mill.getDay().getHotHours() : "", mill != null ? mill.getMtd().getHotHours() : "", norm != null ? norm.getDay().getHotHours() : "", norm != null ? norm.getMtd().getHotHours() : ""},
                {"Production/HR (T/H)", mill != null ? String.valueOf(mill.getDay().getProductionPerHour()) : "", mill != null ? String.valueOf(mill.getMtd().getProductionPerHour()) : "", norm != null ? String.valueOf(norm.getDay().getProductionPerHour()) : "", norm != null ? String.valueOf(norm.getMtd().getProductionPerHour()) : ""},
                {"Utilization %", mill != null ? String.format("%.2f%%", mill.getDay().getUtilizationPct()) : "", mill != null ? String.format("%.2f%%", mill.getMtd().getUtilizationPct()) : "", norm != null ? String.format("%.2f%%", norm.getDay().getUtilizationPct()) : "", norm != null ? String.format("%.2f%%", norm.getMtd().getUtilizationPct()) : ""},
                {"Availability %", mill != null ? String.format("%.2f%%", mill.getDay().getAvailabilityPct()) : "", mill != null ? String.format("%.2f%%", mill.getMtd().getAvailabilityPct()) : "", norm != null ? String.format("%.2f%%", norm.getDay().getAvailabilityPct()) : "", norm != null ? String.format("%.2f%%", norm.getMtd().getAvailabilityPct()) : ""},
                {"Avg Hot Hours (H/Day)", "—", mill != null ? mill.getMtd().getAvgHotHours() : "", "—", norm != null ? norm.getMtd().getAvgHotHours() : ""},
                {"Working Days", mill != null ? String.valueOf(mill.getDay().getWorkingDays()) : "", mill != null ? String.valueOf(mill.getMtd().getWorkingDays()) : "", norm != null ? String.valueOf(norm.getDay().getWorkingDays()) : "", norm != null ? String.valueOf(norm.getMtd().getWorkingDays()) : ""},
                {"No Of Work Shift", mill != null ? String.valueOf(mill.getDay().getShifts()) : "", mill != null ? String.valueOf(mill.getMtd().getShifts()) : "", norm != null ? String.valueOf(norm.getDay().getShifts()) : "", norm != null ? String.valueOf(norm.getMtd().getShifts()) : ""},
            };
            Set<Integer> totals = new HashSet<>(Arrays.asList(9, 10, 11));
            return generateGenericExcel(report, "Plate Mill Production Division - Daily Production Report (DPR)", "MILL", "Mill Section", "NORM", "Normalizing Furnace", rows, totals, reportType, date, month, year, fromDate, toDate);
        }
    }

    private String getDelayDuration(ReportResponse.AreaReport area, String dayOrMtd, String typeCode) {
        if (area == null) return "";
        ReportResponse.Metrics m = "day".equalsIgnoreCase(dayOrMtd) ? area.getDay() : area.getMtd();
        if (m == null || m.getDelayTypeDurations() == null) return "00:00";
        return m.getDelayTypeDurations().getOrDefault(typeCode.toUpperCase(), "00:00");
    }

    private byte[] generateGenericExcel(ReportResponse report, String titleText,
                                         String area1Code, String area1Name,
                                         String area2Code, String area2Name,
                                         String[][] rows, Set<Integer> totalRowIndices,
                                         String reportType, LocalDate date, String month, Integer year,
                                         LocalDate fromDate, LocalDate toDate) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("DPR Report");

            // ---- Styles ----
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);

            CellStyle subHeaderStyle = workbook.createCellStyle();
            Font subHdrFont = workbook.createFont();
            subHdrFont.setBold(true);
            subHdrFont.setColor(IndexedColors.WHITE.getIndex());
            subHeaderStyle.setFont(subHdrFont);
            subHeaderStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            subHeaderStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle labelStyle = workbook.createCellStyle();
            Font labelFont = workbook.createFont();
            labelFont.setBold(true);
            labelStyle.setFont(labelFont);
            labelStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            labelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            labelStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle totalStyle = workbook.createCellStyle();
            Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            titleStyle.setFont(titleFont);

            boolean showDay = "DAILY".equalsIgnoreCase(reportType);
            String monthLabel = "Month";
            if ("DAILY".equalsIgnoreCase(reportType)) monthLabel = "Month (MTD)";
            else if ("MONTHLY".equalsIgnoreCase(reportType)) monthLabel = "Month";
            else if ("YEARLY".equalsIgnoreCase(reportType)) monthLabel = "Year";
            else if ("CUSTOM".equalsIgnoreCase(reportType)) monthLabel = "Range";

            // ---- Title Row ----
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(titleText);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, showDay ? 4 : 2));

            String reportingPeriod = "";
            DateTimeFormatter dtfDay = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            LocalDate activeDate = report.getReportDate();
            if ("DAILY".equalsIgnoreCase(reportType)) {
                reportingPeriod = activeDate.format(dtfDay);
            } else if ("MONTHLY".equalsIgnoreCase(reportType)) {
                reportingPeriod = activeDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            } else if ("YEARLY".equalsIgnoreCase(reportType)) {
                reportingPeriod = String.valueOf(activeDate.getYear());
            } else if ("CUSTOM".equalsIgnoreCase(reportType)) {
                LocalDate sDate = fromDate != null ? fromDate : activeDate.minusDays(30);
                LocalDate eDate = toDate != null ? toDate : activeDate;
                reportingPeriod = sDate.format(dtfDay) + " to " + eDate.format(dtfDay);
            } else {
                reportingPeriod = activeDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            }
            String exportTime = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));

            Row subtitleRow = sheet.createRow(1);
            subtitleRow.createCell(0).setCellValue("Shop: " + report.getShopName()
                    + "   |   Reporting Period: " + reportingPeriod
                    + "   |   Export Time: " + exportTime);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, showDay ? 4 : 2));

            sheet.createRow(2); // blank

            // ---- Column Headers ----
            Row colHeaderRow1 = sheet.createRow(3);
            Cell paramHeader = colHeaderRow1.createCell(0);
            paramHeader.setCellValue("Delay Type / Parameter");
            paramHeader.setCellStyle(headerStyle);

            Cell area1Header = colHeaderRow1.createCell(1);
            area1Header.setCellValue(area1Name);
            area1Header.setCellStyle(headerStyle);
            if (showDay) sheet.addMergedRegion(new CellRangeAddress(3, 3, 1, 2));

            Cell area2Header = colHeaderRow1.createCell(showDay ? 3 : 2);
            area2Header.setCellValue(area2Name);
            area2Header.setCellStyle(headerStyle);
            if (showDay) sheet.addMergedRegion(new CellRangeAddress(3, 3, showDay ? 3 : 2, showDay ? 4 : 3));

            Row colHeaderRow2 = sheet.createRow(4);
            colHeaderRow2.createCell(0).setCellStyle(subHeaderStyle);
            if (showDay) {
                Cell a1Day = colHeaderRow2.createCell(1);
                a1Day.setCellValue(area1Name + " Day (HH:MM)");
                a1Day.setCellStyle(subHeaderStyle);
                Cell a1Month = colHeaderRow2.createCell(2);
                a1Month.setCellValue(area1Name + " Cumulative (" + monthLabel + " HH:MM)");
                a1Month.setCellStyle(subHeaderStyle);
                Cell a2Day = colHeaderRow2.createCell(3);
                a2Day.setCellValue(area2Name + " Day (HH:MM)");
                a2Day.setCellStyle(subHeaderStyle);
                Cell a2Month = colHeaderRow2.createCell(4);
                a2Month.setCellValue(area2Name + " Cumulative (" + monthLabel + " HH:MM)");
                a2Month.setCellStyle(subHeaderStyle);
            } else {
                Cell a1Month = colHeaderRow2.createCell(1);
                a1Month.setCellValue(area1Name + " Cumulative (" + monthLabel + " HH:MM)");
                a1Month.setCellStyle(subHeaderStyle);
                Cell a2Month = colHeaderRow2.createCell(2);
                a2Month.setCellValue(area2Name + " Cumulative (" + monthLabel + " HH:MM)");
                a2Month.setCellStyle(subHeaderStyle);
            }

            int rowNum = 5;
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(rowNum++);
                String[] rowData = rows[i];
                CellStyle style = totalRowIndices.contains(i) ? totalStyle : dataStyle;

                int c = 0;
                Cell labelCell = row.createCell(c);
                labelCell.setCellValue(rowData[0]);
                labelCell.setCellStyle(labelStyle);
                c++;

                if (showDay) {
                    Cell c1 = row.createCell(c); c1.setCellValue(rowData[1]); c1.setCellStyle(style); c++;
                    Cell c2 = row.createCell(c); c2.setCellValue(rowData[2]); c2.setCellStyle(style); c++;
                    Cell c3 = row.createCell(c); c3.setCellValue(rowData[3]); c3.setCellStyle(style); c++;
                    Cell c4 = row.createCell(c); c4.setCellValue(rowData[4]); c4.setCellStyle(style); c++;
                } else {
                    Cell c1 = row.createCell(c); c1.setCellValue(rowData[2]); c1.setCellStyle(style); c++;
                    Cell c2 = row.createCell(c); c2.setCellValue(rowData[4]); c2.setCellStyle(style); c++;
                }
            }

            // ---- Column Widths ----
            sheet.setColumnWidth(0, 40 * 256);
            if (showDay) {
                sheet.setColumnWidth(1, 22 * 256);
                sheet.setColumnWidth(2, 22 * 256);
                sheet.setColumnWidth(3, 22 * 256);
                sheet.setColumnWidth(4, 22 * 256);
            } else {
                sheet.setColumnWidth(1, 22 * 256);
                sheet.setColumnWidth(2, 22 * 256);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String getMetricValueByKey(ReportResponse.Metrics m, String key, boolean isDay) {
        if (m == null) return "";
        switch (key) {
            case "planned": return m.getDelayTypeDurations().getOrDefault("PLANNED", "00:00");
            case "electrical": return m.getDelayTypeDurations().getOrDefault("ELEC", "00:00");
            case "mechanical": return m.getDelayTypeDurations().getOrDefault("MECH", "00:00");
            case "operation": return m.getDelayTypeDurations().getOrDefault("OPER", "00:00");
            case "sbs": return m.getDelayTypeDurations().getOrDefault("SBS", "00:00");
            case "fuelEmd": return m.getDelayTypeDurations().getOrDefault("FUEL", "00:00");
            case "power": return m.getDelayTypeDurations().getOrDefault("POWER", "00:00");
            case "msds": return m.getDelayTypeDurations().getOrDefault("MSDS", "00:00");
            case "others": return m.getDelayTypeDurations().getOrDefault("OTHERS", "00:00");
            case "gasPowerShortage": return m.getDelayTypeDurations().getOrDefault("GAS_POWER_SHORTAGE", "00:00");
            case "rmShortage": return m.getDelayTypeDurations().getOrDefault("RM_SHORTAGE", "00:00");
            case "totalDelay": return m.getTotalDelay();
            case "controllable": return m.getControllable();
            case "nonControllable": return m.getNonControllable();
            case "availableHours": return m.getAvailableHours();
            case "hotHours": return m.getHotHours();
            case "productionPerHour": return String.valueOf(m.getProductionPerHour());
            case "utilizationPct": return m.getUtilizationPct() + "%";
            case "availabilityPct": return m.getAvailabilityPct() + "%";
            case "repairHours": return m.getRepairHours();
            case "avgHotHours": return isDay ? "—" : m.getAvgHotHours();
            case "workingDays": return String.valueOf(m.getWorkingDays());
            case "shifts": return String.valueOf(m.getShifts());
            default: return "";
        }
    }

    @Override
    public byte[] generatePdfReport(Long shopId, Long areaId, String reportType, LocalDate date, String month, Integer year, LocalDate fromDate, LocalDate toDate, Long delayTypeId, String shift) throws IOException {
        // 1. Resolve date range
        LocalDate startDate;
        LocalDate endDate;
        LocalDate activeDate = date != null ? date : LocalDate.now();

        if ("DAILY".equalsIgnoreCase(reportType)) {
            startDate = activeDate.withDayOfMonth(1); // month-to-date up to active date
            endDate = activeDate;
        } else if ("MONTHLY".equalsIgnoreCase(reportType)) {
            if (month != null && !month.trim().isEmpty()) {
                YearMonth ym = YearMonth.parse(month);
                startDate = ym.atDay(1);
                endDate = ym.atEndOfMonth();
            } else {
                startDate = activeDate.withDayOfMonth(1);
                endDate = activeDate.withDayOfMonth(activeDate.lengthOfMonth());
            }
            activeDate = endDate;
        } else if ("YEARLY".equalsIgnoreCase(reportType)) {
            int targetYear = year != null ? year : activeDate.getYear();
            startDate = LocalDate.of(targetYear, 4, 1);
            endDate = LocalDate.of(targetYear + 1, 3, 31);
            activeDate = endDate;
        } else if ("CUSTOM".equalsIgnoreCase(reportType)) {
            startDate = fromDate != null ? fromDate : activeDate.minusDays(30);
            endDate = toDate != null ? toDate : activeDate;
            activeDate = endDate;
        } else {
            startDate = activeDate.withDayOfMonth(1);
            endDate = activeDate;
        }

        // 2. Fetch Reference Entities
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found for ID: " + shopId));

        List<ShopArea> areas = shopAreaRepository.findByShopId(shopId);
        Map<Long, ShopArea> areaMap = areas.stream()
                .collect(Collectors.toMap(ShopArea::getAreaId, a -> a));
        if (areaId != null) {
            areas = areas.stream()
                    .filter(a -> a.getAreaId().equals(areaId))
                    .collect(Collectors.toList());
            if (areas.isEmpty()) {
                throw new ResourceNotFoundException("Shop Area not found for ID: " + areaId + " under Shop: " + shopId);
            }
        }

        List<DelayType> delayTypes = delayTypeRepository.findAll();
        Map<Long, DelayType> delayTypeMap = delayTypes.stream()
                .collect(Collectors.toMap(DelayType::getDelayTypeId, dt -> dt));

        String selectedDelayTypeName = "All";
        if (delayTypeId != null) {
            DelayType selectedType = delayTypeMap.get(delayTypeId);
            if (selectedType != null) {
                selectedDelayTypeName = selectedType.getTypeName();
            }
        }

        String selectedAreaName = "All Areas";
        if (areaId != null) {
            ShopArea selectedArea = areaMap.get(areaId);
            if (selectedArea != null) {
                selectedAreaName = selectedArea.getAreaName();
            }
        }

        String selectedShift = (shift == null || "All".equalsIgnoreCase(shift)) ? "All Shifts" : shift;

        String reportingPeriod = "";
        DateTimeFormatter dtfDay = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        if ("DAILY".equalsIgnoreCase(reportType)) {
            reportingPeriod = activeDate.format(dtfDay);
        } else if ("MONTHLY".equalsIgnoreCase(reportType)) {
            reportingPeriod = activeDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        } else if ("YEARLY".equalsIgnoreCase(reportType)) {
            reportingPeriod = "FY " + activeDate.getYear() + "-" + (activeDate.getYear() + 1);
        } else if ("CUSTOM".equalsIgnoreCase(reportType)) {
            LocalDate sDate = fromDate != null ? fromDate : activeDate.minusDays(30);
            LocalDate eDate = toDate != null ? toDate : activeDate;
            reportingPeriod = sDate.format(dtfDay) + " to " + eDate.format(dtfDay);
        } else {
            reportingPeriod = activeDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        }

        // 3. Fetch Delays and Operational Logs
        List<DelayLog> delays = delayLogRepository.findFilteredDelays(shopId, startDate, endDate, areaId, (shift == null || "All".equalsIgnoreCase(shift)) ? null : shift, delayTypeId);
        List<ShiftOperationalLog> opLogs = shiftOperationalLogRepository.findFilteredOperationalLogs(shopId, startDate, endDate, areaId);
        Map<Long, ShiftOperationalLog> opLogMap = opLogs.stream()
                .collect(Collectors.toMap(ShiftOperationalLog::getOperationalLogId, o -> o));

        // 4. Map and Sort DelayLogs
        List<DelayLogResponse> mappedLogs = delays.stream()
                .map(dl -> {
                    ShiftOperationalLog sol = opLogMap.get(dl.getOperationalLogId());
                    if (sol == null) return null;
                    ShopArea area = areaMap.get(sol.getAreaId());
                    if (area == null) return null;
                    DelayType type = delayTypeMap.get(dl.getDelayTypeId());
                    if (type == null) return null;

                    String formattedDuration = String.format("%02d:%02d", dl.getDelayHours(), dl.getDelayMinutes());
                    return DelayLogResponse.builder()
                            .logId(dl.getDelayLogId())
                            .areaId(area.getAreaId())
                            .areaName(area.getAreaName())
                            .delayTypeId(dl.getDelayTypeId())
                            .typeName(type.getTypeName())
                            .delayGroup(type.getDelayGroup())
                            .logDate(sol.getLogDate())
                            .delayHours(dl.getDelayHours())
                            .delayMinutes(dl.getDelayMinutes())
                            .remarks(dl.getRemarks())
                            .durationHHMM(formattedDuration)
                            .startTime(dl.getStartTime())
                            .endTime(dl.getEndTime())
                            .shift(dl.getShift())
                            .durationMinutes(dl.getDurationMinutes())
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted((r1, r2) -> {
                    int c = r1.getLogDate().compareTo(r2.getLogDate());
                    if (c != 0) return c;
                    c = r1.getAreaName().compareTo(r2.getAreaName());
                    if (c != 0) return c;
                    c = r1.getTypeName().compareTo(r2.getTypeName());
                    if (c != 0) return c;
                    String s1 = r1.getStartTime() != null ? r1.getStartTime() : "";
                    String s2 = r2.getStartTime() != null ? r2.getStartTime() : "";
                    int sc = s1.compareTo(s2);
                    if (sc != 0) return sc;
                    return r1.getLogId().compareTo(r2.getLogId());
                })
                .collect(Collectors.toList());

        // 5. Pre-calculate Metrics
        int totalEvents = mappedLogs.size();
        int totalDurationMinutes = mappedLogs.stream()
                .mapToInt(log -> {
                    int hrs = log.getDelayHours() != null ? log.getDelayHours() : 0;
                    int mins = log.getDelayMinutes() != null ? log.getDelayMinutes() : 0;
                    return (hrs * 60) + mins;
                })
                .sum();
        String totalDurationStr = String.format("%02d:%02d", totalDurationMinutes / 60, totalDurationMinutes % 60);

        boolean isSMS = shopId == 2;
        String[] groups = isSMS ?
                new String[]{"Planned", "Electrical", "Mechanical", "Operation", "SBS", "Fuel/EMD", "Power", "MSDS", "Gas/Power Shortage", "RM Shortage", "Others"} :
                new String[]{"Planned", "Electrical", "Mechanical", "Operation", "SBS", "Fuel/EMD", "Power", "MSDS", "Others"};

        Map<String, Integer> groupCounts = new LinkedHashMap<>();
        Map<String, Integer> groupDurations = new LinkedHashMap<>();
        for (String g : groups) {
            groupCounts.put(g, 0);
            groupDurations.put(g, 0);
        }

        for (DelayLogResponse log : mappedLogs) {
            DelayType type = delayTypeMap.get(log.getDelayTypeId());
            String key = "Others";
            if (type != null) {
                String code = type.getTypeCode().toUpperCase();
                switch (code) {
                    case "PLANNED": key = "Planned"; break;
                    case "ELEC": key = "Electrical"; break;
                    case "MECH": key = "Mechanical"; break;
                    case "OPER": key = "Operation"; break;
                    case "SBS": key = "SBS"; break;
                    case "FUEL": key = "Fuel/EMD"; break;
                    case "POWER": key = "Power"; break;
                    case "MSDS": key = "MSDS"; break;
                    case "GAS_POWER_SHORTAGE": key = "Gas/Power Shortage"; break;
                    case "RM_SHORTAGE": key = "RM Shortage"; break;
                    default: key = "Others"; break;
                }
            }
            groupCounts.put(key, groupCounts.get(key) + 1);
            int hrs = log.getDelayHours() != null ? log.getDelayHours() : 0;
            int mins = log.getDelayMinutes() != null ? log.getDelayMinutes() : 0;
            int logMins = (hrs * 60) + mins;
            groupDurations.put(key, groupDurations.get(key) + logMins);
        }     

        // 6. Generate Landscape PDF Document
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);

        // Footer Setup
        com.lowagie.text.Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(74, 85, 104));
        writer.setPageEvent(new PDFPageEventHelper(footerFont));

        document.open();

        // 7. Title Section
        com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(15, 32, 67));
        com.lowagie.text.Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(74, 85, 104));
        com.lowagie.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(15, 32, 67));

        Paragraph titlePara = new Paragraph("BHILAI STEEL PLANT (BSP)", titleFont);
        titlePara.setAlignment(Element.ALIGN_CENTER);
        document.add(titlePara);

        Paragraph subTitlePara = new Paragraph("Delay Management System", subtitleFont);
        subTitlePara.setAlignment(Element.ALIGN_CENTER);
        document.add(subTitlePara);

        Paragraph reportTitlePara = new Paragraph("Delay Events Report", headerFont);
        reportTitlePara.setAlignment(Element.ALIGN_CENTER);
        reportTitlePara.setSpacingAfter(10f);
        document.add(reportTitlePara);

        // 8. Metadata Section
        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setSpacingAfter(10f);

        com.lowagie.text.Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(74, 85, 104));
        com.lowagie.text.Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        PdfPCell cellLeft = new PdfPCell();
        cellLeft.setBorder(PdfPCell.NO_BORDER);
        Paragraph pLeft = new Paragraph();
        pLeft.add(new Phrase("Shop          : ", labelFont));
        pLeft.add(new Phrase(shop.getShopName() + "\n", valueFont));
        pLeft.add(new Phrase("Area          : ", labelFont));
        pLeft.add(new Phrase(selectedAreaName + "\n", valueFont));
        pLeft.add(new Phrase("Delay Type    : ", labelFont));
        pLeft.add(new Phrase(selectedDelayTypeName, valueFont));
        cellLeft.addElement(pLeft);

        PdfPCell cellRight = new PdfPCell();
        cellRight.setBorder(PdfPCell.NO_BORDER);
        Paragraph pRight = new Paragraph();
        pRight.setAlignment(Element.ALIGN_RIGHT);
        pRight.add(new Phrase("Period        : ", labelFont));
        pRight.add(new Phrase(reportingPeriod + "\n", valueFont));
        pRight.add(new Phrase("Shift         : ", labelFont));
        pRight.add(new Phrase(selectedShift + "\n", valueFont));
        pRight.add(new Phrase("Generated On  : ", labelFont));
        String generatedOn = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a"));
        pRight.add(new Phrase(generatedOn, valueFont));
        cellRight.addElement(pRight);

        metaTable.addCell(cellLeft);
        metaTable.addCell(cellRight);
        document.add(metaTable);

        // 9. Summary KPI Table
        Paragraph summaryHeader = new Paragraph("SUMMARY OF DELAYS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(15, 32, 67)));
        summaryHeader.setSpacingAfter(4f);
        document.add(summaryHeader);

        PdfPTable kpiTable = new PdfPTable(2);
        kpiTable.setWidthPercentage(100);
        kpiTable.setWidths(new float[]{30f, 70f});
        kpiTable.setSpacingAfter(12f);

        PdfPCell totalsCell = new PdfPCell();
        totalsCell.setBackgroundColor(new Color(240, 244, 248));
        totalsCell.setBorderColor(new Color(200, 210, 220));
        totalsCell.setPadding(8f);

        Paragraph tEvents = new Paragraph();
        tEvents.add(new Phrase("Total Delay Events\n", FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(74, 85, 104))));
        tEvents.add(new Phrase(String.valueOf(totalEvents) + "\n\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(15, 32, 67))));
        tEvents.add(new Phrase("Total Delay Duration\n", FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(74, 85, 104))));
        tEvents.add(new Phrase(totalDurationStr + " (HH:MM)\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(15, 32, 67))));
        totalsCell.addElement(tEvents);
        kpiTable.addCell(totalsCell);

        PdfPCell breakdownCell = new PdfPCell();
        breakdownCell.setBorderColor(new Color(200, 210, 220));
        breakdownCell.setPadding(6f);

        PdfPTable gridTable = new PdfPTable(3);
        gridTable.setWidthPercentage(100);

        com.lowagie.text.Font groupTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(15, 32, 67));
        com.lowagie.text.Font groupDataFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);

        for (String g : groups) {
            int count = groupCounts.get(g);
            int minutes = groupDurations.get(g);
            String durationStr = String.format("%02d:%02d", minutes / 60, minutes % 60);

            PdfPCell cell = new PdfPCell();
            cell.setBorder(PdfPCell.NO_BORDER);
            cell.setPadding(3f);

            Paragraph gp = new Paragraph();
            gp.add(new Phrase(g + "\n", groupTitleFont));
            gp.add(new Phrase("Events: " + count + "  |  " + durationStr, groupDataFont));
            cell.addElement(gp);

            gridTable.addCell(cell);
        }

        breakdownCell.addElement(gridTable);
        kpiTable.addCell(breakdownCell);
        document.add(kpiTable);

        // 10. Detailed Logs Table
        Paragraph tableHeader = new Paragraph("DETAILED DELAY LOGS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(15, 32, 67)));
        tableHeader.setSpacingAfter(4f);
        document.add(tableHeader);

        float[] widths = {4f, 10f, 14f, 8f, 18f, 9f, 37f};
        PdfPTable logTable = new PdfPTable(widths);
        logTable.setWidthPercentage(100);
        logTable.setHeaderRows(1);

        com.lowagie.text.Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        com.lowagie.text.Font tableBodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);

        String[] headers = {"S.No", "Date", "Area", "Shift", "Delay Type", "Duration", "Remarks"};
        for (String header : headers) {
            PdfPCell headerCell = new PdfPCell(new Phrase(header, tableHeaderFont));
            headerCell.setBackgroundColor(new Color(15, 32, 67));
            headerCell.setPadding(5f);
            headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            headerCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            headerCell.setBorderColor(new Color(200, 210, 220));
            logTable.addCell(headerCell);
        }

        int sNo = 1;
        if (mappedLogs.isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No delay logs found matching the selected filters.", tableBodyFont));
            emptyCell.setColspan(7);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            emptyCell.setPadding(8f);
            emptyCell.setBorderColor(new Color(200, 210, 220));
            logTable.addCell(emptyCell);
        } else {
            for (DelayLogResponse log : mappedLogs) {
                PdfPCell cSNo = new PdfPCell(new Phrase(String.valueOf(sNo++), tableBodyFont));
                cSNo.setHorizontalAlignment(Element.ALIGN_CENTER);

                PdfPCell cDate = new PdfPCell(new Phrase(log.getLogDate().toString(), tableBodyFont));
                cDate.setHorizontalAlignment(Element.ALIGN_CENTER);

                PdfPCell cArea = new PdfPCell(new Phrase(log.getAreaName(), tableBodyFont));

                PdfPCell cShift = new PdfPCell(new Phrase(log.getShift(), tableBodyFont));
                cShift.setHorizontalAlignment(Element.ALIGN_CENTER);

                PdfPCell cType = new PdfPCell(new Phrase(log.getTypeName(), tableBodyFont));

                PdfPCell cDuration = new PdfPCell(new Phrase(log.getDurationHHMM(), tableBodyFont));
                cDuration.setHorizontalAlignment(Element.ALIGN_CENTER);

                PdfPCell cRemarks = new PdfPCell(new Phrase(log.getRemarks(), tableBodyFont));

                Color bgColor = (sNo % 2 == 0) ? new Color(245, 247, 250) : Color.WHITE;

                for (PdfPCell cell : new PdfPCell[]{cSNo, cDate, cArea, cShift, cType, cDuration, cRemarks}) {
                    cell.setPadding(4f);
                    cell.setBackgroundColor(bgColor);
                    cell.setBorderColor(new Color(220, 225, 230));
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    logTable.addCell(cell);
                }
            }
        }

        document.add(logTable);
        document.close();

        return baos.toByteArray();
    }

    public static class PDFPageEventHelper extends PdfPageEventHelper {
        private PdfTemplate totalPages;
        private final com.lowagie.text.Font footerFont;

        public PDFPageEventHelper(com.lowagie.text.Font footerFont) {
            this.footerFont = footerFont;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPages = writer.getDirectContent().createTemplate(30, 16);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfPTable footerTable = new PdfPTable(3);
            try {
                footerTable.setWidths(new float[]{60f, 33f, 7f});
                footerTable.setTotalWidth(document.right() - document.left());
                footerTable.setLockedWidth(true);

                PdfPCell leftCell = new PdfPCell(new Phrase("Generated by BSP Delay Management System", footerFont));
                leftCell.setBorder(PdfPCell.NO_BORDER);
                leftCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                footerTable.addCell(leftCell);

                String pageText = String.format("Page %d of ", writer.getPageNumber());
                PdfPCell rightCell = new PdfPCell(new Phrase(pageText, footerFont));
                rightCell.setBorder(PdfPCell.NO_BORDER);
                rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                footerTable.addCell(rightCell);

                PdfPCell templateCell = new PdfPCell(Image.getInstance(totalPages));
                templateCell.setBorder(PdfPCell.NO_BORDER);
                templateCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                templateCell.setPaddingTop(2f);
                footerTable.addCell(templateCell);

                footerTable.writeSelectedRows(0, -1, document.left(), document.bottom() - 15, writer.getDirectContent());
            } catch (Exception e) {
                // ignore
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalPages.beginText();
            try {
                BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                totalPages.setFontAndSize(bf, 8);
                totalPages.showText(String.valueOf(writer.getPageNumber() - 1));
            } catch (Exception e) {
                // ignore
            }
            totalPages.endText();
        }
    }

    @Override
    public String getPdfReportFilename(Long shopId, Long areaId, String reportType, LocalDate date, String month, Integer year, LocalDate fromDate, LocalDate toDate) {
        Shop shop = shopRepository.findById(shopId).orElse(null);
        String shopCode = "SHOP";
        if (shop != null) {
            if ("Plate Mill".equalsIgnoreCase(shop.getShopName())) {
                shopCode = "PM";
            } else {
                shopCode = shop.getShopName().replaceAll("\\s+", "_");
            }
        }

        String areaCode = "";
        if (areaId != null) {
            ShopArea area = shopAreaRepository.findById(areaId).orElse(null);
            if (area != null) {
                areaCode = "_" + area.getAreaName().replaceAll("\\s+", "_").toUpperCase();
            }
        }

        LocalDate activeDate = date != null ? date : LocalDate.now();
        String filePeriod = "";
        if ("DAILY".equalsIgnoreCase(reportType)) {
            filePeriod = activeDate.toString(); // YYYY-MM-DD
        } else if ("MONTHLY".equalsIgnoreCase(reportType)) {
            if (month != null && !month.trim().isEmpty()) {
                try {
                    YearMonth ym = YearMonth.parse(month);
                    filePeriod = ym.format(DateTimeFormatter.ofPattern("MMM_yyyy"));
                } catch (Exception e) {
                    filePeriod = activeDate.format(DateTimeFormatter.ofPattern("MMM_yyyy"));
                }
            } else {
                filePeriod = activeDate.format(DateTimeFormatter.ofPattern("MMM_yyyy"));
            }
        } else if ("YEARLY".equalsIgnoreCase(reportType)) {
            int targetYear = year != null ? year : activeDate.getYear();
            filePeriod = targetYear + "_" + (targetYear + 1);
        } else if ("CUSTOM".equalsIgnoreCase(reportType)) {
            LocalDate sDate = fromDate != null ? fromDate : activeDate.minusDays(30);
            LocalDate eDate = toDate != null ? toDate : activeDate;
            filePeriod = sDate.toString() + "_to_" + eDate.toString();
        } else {
            filePeriod = activeDate.toString();
        }

        return "Delay_Events_" + shopCode + areaCode + "_" + filePeriod + ".pdf";
    }
}

