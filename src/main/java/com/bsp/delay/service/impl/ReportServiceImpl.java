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
                .areaReports(areaReportsMap)
                .charts(charts)
                .build();
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
            }
        }

        Map<String, Double> dayDelaysByGroup = calculateDelaysByGroup(dayDelays, delayTypeMap);
        ReportResponse.Metrics dayMetrics = compileMetrics(dayAvailHours, dayProductionQty, 0.0, dayWorkingDays, dayShifts, dayDelaysByGroup, 1, 0.0);

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
                periodProductionQty += ("MILL".equalsIgnoreCase(area.getAreaCode()) ? 4000.0 : 600.0);
            }
        }

        Map<String, Double> periodDelaysByGroup = calculateDelaysByGroup(areaDelays, delayTypeMap);
        double periodTotalDelay = periodDelaysByGroup.values().stream().mapToDouble(Double::doubleValue).sum();
        double periodHotHours = Math.max(0.0, periodAvailHours - periodTotalDelay);

        ReportResponse.Metrics periodMetrics = compileMetrics(periodAvailHours, periodProductionQty, 0.0, periodWorkingDays, periodShifts, periodDelaysByGroup, calendarDays, periodHotHours);

        return ReportResponse.AreaReport.builder()
                .areaId(area.getAreaId())
                .areaName(area.getAreaName())
                .areaCode(area.getAreaCode())
                .day(dayMetrics)
                .mtd(periodMetrics)
                .build();
    }

    private Map<String, Double> calculateDelaysByGroup(List<DelayLog> logs, Map<Long, DelayType> delayTypeMap) {
        Map<String, Double> delaySum = new HashMap<>();
        String[] groups = {"Planned", "Electrical", "Mechanical", "Operation", "SBS", "Fuel/EMD", "Power", "MSDS", "Others"};
        for (String g : groups) {
            delaySum.put(g, 0.0);
        }

        for (DelayLog log : logs) {
            DelayType type = delayTypeMap.get(log.getDelayTypeId());
            if (type != null) {
                double duration = log.getDelayHours() + (log.getDelayMinutes() / 60.0);
                String code = type.getTypeCode().toUpperCase();
                String key;
                switch (code) {
                    case "PLANNED": key = "Planned"; break;
                    case "ELEC": key = "Electrical"; break;
                    case "MECH": key = "Mechanical"; break;
                    case "OPER": key = "Operation"; break;
                    case "SBS": key = "SBS"; break;
                    case "FUEL": key = "Fuel/EMD"; break;
                    case "POWER": key = "Power"; break;
                    case "MSDS": key = "MSDS"; break;
                    case "OTHERS":
                    default:
                        key = "Others"; break;
                }
                delaySum.put(key, delaySum.getOrDefault(key, 0.0) + duration);
            }
        }
        return delaySum;
    }

    private double calculateAvailability(double availableHours, double targetCalendarHours) {
        if (targetCalendarHours <= 0) return 0.0;
        return (availableHours / targetCalendarHours) * 100.0;
    }

    private ReportResponse.Metrics compileMetrics(double availHours, double productionQty, double repairHours,
                                                  double workingDays, double shifts,
                                                  Map<String, Double> delays, int calendarDays, double totalPeriodHotHours) {
        double planned = delays.getOrDefault("Planned", 0.0);
        double electrical = delays.getOrDefault("Electrical", 0.0);
        double mechanical = delays.getOrDefault("Mechanical", 0.0);
        double operation = delays.getOrDefault("Operation", 0.0);
        double sbs = delays.getOrDefault("SBS", 0.0);
        double fuelEmd = delays.getOrDefault("Fuel/EMD", 0.0);
        double power = delays.getOrDefault("Power", 0.0);
        double msds = delays.getOrDefault("MSDS", 0.0);
        double others = delays.getOrDefault("Others", 0.0);

        double controllable = electrical + mechanical + operation;
        double nonControllable = sbs + fuelEmd + power + msds + others;
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

        return ReportResponse.Metrics.builder()
                .planned(formatHoursToHHMM(planned))
                .electrical(formatHoursToHHMM(electrical))
                .mechanical(formatHoursToHHMM(mechanical))
                .operation(formatHoursToHHMM(operation))
                .sbs(formatHoursToHHMM(sbs))
                .fuelEmd(formatHoursToHHMM(fuelEmd))
                .power(formatHoursToHHMM(power))
                .msds(formatHoursToHHMM(msds))
                .others(formatHoursToHHMM(others))
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
            titleCell.setCellValue("Plate Mill Production Division - Daily Production Report (DPR)");
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

            Cell millHeader = colHeaderRow1.createCell(1);
            millHeader.setCellValue("Mill Section");
            millHeader.setCellStyle(headerStyle);
            if (showDay) sheet.addMergedRegion(new CellRangeAddress(3, 3, 1, 2));

            Cell normHeader = colHeaderRow1.createCell(showDay ? 3 : 2);
            normHeader.setCellValue("Normalizing Furnace");
            normHeader.setCellStyle(headerStyle);
            if (showDay) sheet.addMergedRegion(new CellRangeAddress(3, 3, 3, 4));

            Row colHeaderRow2 = sheet.createRow(4);
            colHeaderRow2.createCell(0).setCellStyle(subHeaderStyle);
            if (showDay) {
                Cell millDay = colHeaderRow2.createCell(1);
                millDay.setCellValue("Mill Day (HH:MM)");
                millDay.setCellStyle(subHeaderStyle);
                Cell millMonth = colHeaderRow2.createCell(2);
                millMonth.setCellValue("Mill Cumulative (" + monthLabel + " HH:MM)");
                millMonth.setCellStyle(subHeaderStyle);
                Cell normDay = colHeaderRow2.createCell(3);
                normDay.setCellValue("Norm Day (HH:MM)");
                normDay.setCellStyle(subHeaderStyle);
                Cell normMonth = colHeaderRow2.createCell(4);
                normMonth.setCellValue("Norm Cumulative (" + monthLabel + " HH:MM)");
                normMonth.setCellStyle(subHeaderStyle);
            } else {
                Cell millMonth = colHeaderRow2.createCell(1);
                millMonth.setCellValue("Mill Cumulative (" + monthLabel + " HH:MM)");
                millMonth.setCellStyle(subHeaderStyle);
                Cell normMonth = colHeaderRow2.createCell(2);
                normMonth.setCellValue("Norm Cumulative (" + monthLabel + " HH:MM)");
                normMonth.setCellStyle(subHeaderStyle);
            }

            // ---- Data Rows ----
            ReportResponse.AreaReport millArea = report.getAreaReports() != null ? report.getAreaReports().get("MILL") : null;
            ReportResponse.AreaReport normArea = report.getAreaReports() != null ? report.getAreaReports().get("NORM") : null;

            String[][] rows = {
                    {"Planned", millArea != null ? millArea.getDay().getPlanned() : "", millArea != null ? millArea.getMtd().getPlanned() : "",
                            normArea != null ? normArea.getDay().getPlanned() : "", normArea != null ? normArea.getMtd().getPlanned() : ""},
                    {"Electrical", millArea != null ? millArea.getDay().getElectrical() : "", millArea != null ? millArea.getMtd().getElectrical() : "",
                            normArea != null ? normArea.getDay().getElectrical() : "", normArea != null ? normArea.getMtd().getElectrical() : ""},
                    {"Mechanical", millArea != null ? millArea.getDay().getMechanical() : "", millArea != null ? millArea.getMtd().getMechanical() : "",
                            normArea != null ? normArea.getDay().getMechanical() : "", normArea != null ? normArea.getMtd().getMechanical() : ""},
                    {"Operation", millArea != null ? millArea.getDay().getOperation() : "", millArea != null ? millArea.getMtd().getOperation() : "",
                            normArea != null ? normArea.getDay().getOperation() : "", normArea != null ? normArea.getMtd().getOperation() : ""},
                    {"SBS", millArea != null ? millArea.getDay().getSbs() : "", millArea != null ? millArea.getMtd().getSbs() : "",
                            normArea != null ? normArea.getDay().getSbs() : "", normArea != null ? normArea.getMtd().getSbs() : ""},
                    {"Fuel/EMD", millArea != null ? millArea.getDay().getFuelEmd() : "", millArea != null ? millArea.getMtd().getFuelEmd() : "",
                            normArea != null ? normArea.getDay().getFuelEmd() : "", normArea != null ? normArea.getMtd().getFuelEmd() : ""},
                    {"Power", millArea != null ? millArea.getDay().getPower() : "", millArea != null ? millArea.getMtd().getPower() : "",
                            normArea != null ? normArea.getDay().getPower() : "", normArea != null ? normArea.getMtd().getPower() : ""},
                    {"MSDS", millArea != null ? millArea.getDay().getMsds() : "", millArea != null ? millArea.getMtd().getMsds() : "",
                            normArea != null ? normArea.getDay().getMsds() : "", normArea != null ? normArea.getMtd().getMsds() : ""},
                    {"Others", millArea != null ? millArea.getDay().getOthers() : "", millArea != null ? millArea.getMtd().getOthers() : "",
                            normArea != null ? normArea.getDay().getOthers() : "", normArea != null ? normArea.getMtd().getOthers() : ""},
                    {"Total Delay", millArea != null ? millArea.getDay().getTotalDelay() : "", millArea != null ? millArea.getMtd().getTotalDelay() : "",
                            normArea != null ? normArea.getDay().getTotalDelay() : "", normArea != null ? normArea.getMtd().getTotalDelay() : ""},
                    {"Controllable", millArea != null ? millArea.getDay().getControllable() : "", millArea != null ? millArea.getMtd().getControllable() : "",
                            normArea != null ? normArea.getDay().getControllable() : "", normArea != null ? normArea.getMtd().getControllable() : ""},
                    {"Non-Controllable", millArea != null ? millArea.getDay().getNonControllable() : "", millArea != null ? millArea.getMtd().getNonControllable() : "",
                            normArea != null ? normArea.getDay().getNonControllable() : "", normArea != null ? normArea.getMtd().getNonControllable() : ""},
                    {"Available Hours", millArea != null ? millArea.getDay().getAvailableHours() : "", millArea != null ? millArea.getMtd().getAvailableHours() : "",
                            normArea != null ? normArea.getDay().getAvailableHours() : "", normArea != null ? normArea.getMtd().getAvailableHours() : ""},
                    {"Hot Hours", millArea != null ? millArea.getDay().getHotHours() : "", millArea != null ? millArea.getMtd().getHotHours() : "",
                            normArea != null ? normArea.getDay().getHotHours() : "", normArea != null ? normArea.getMtd().getHotHours() : ""},
                    {"Production/HR (T/H)",
                            millArea != null ? String.valueOf(millArea.getDay().getProductionPerHour()) : "",
                            millArea != null ? String.valueOf(millArea.getMtd().getProductionPerHour()) : "",
                            normArea != null ? String.valueOf(normArea.getDay().getProductionPerHour()) : "",
                            normArea != null ? String.valueOf(normArea.getMtd().getProductionPerHour()) : ""},
                    {"Utilization %",
                            millArea != null ? String.format("%.2f%%", millArea.getDay().getUtilizationPct()) : "",
                            millArea != null ? String.format("%.2f%%", millArea.getMtd().getUtilizationPct()) : "",
                            normArea != null ? String.format("%.2f%%", normArea.getDay().getUtilizationPct()) : "",
                            normArea != null ? String.format("%.2f%%", normArea.getMtd().getUtilizationPct()) : ""},
                    {"Availability %",
                            millArea != null ? String.format("%.2f%%", millArea.getDay().getAvailabilityPct()) : "",
                            millArea != null ? String.format("%.2f%%", millArea.getMtd().getAvailabilityPct()) : "",
                            normArea != null ? String.format("%.2f%%", normArea.getDay().getAvailabilityPct()) : "",
                            normArea != null ? String.format("%.2f%%", normArea.getMtd().getAvailabilityPct()) : ""},
                    {"Avg Hot Hours (H/Day)", "—",
                            millArea != null ? millArea.getMtd().getAvgHotHours() : "",
                            "—",
                            normArea != null ? normArea.getMtd().getAvgHotHours() : ""},
                    {"Working Days",
                            millArea != null ? String.valueOf(millArea.getDay().getWorkingDays()) : "",
                            millArea != null ? String.valueOf(millArea.getMtd().getWorkingDays()) : "",
                            normArea != null ? String.valueOf(normArea.getDay().getWorkingDays()) : "",
                            normArea != null ? String.valueOf(normArea.getMtd().getWorkingDays()) : ""},
                    {"No Of Work Shift",
                            millArea != null ? String.valueOf(millArea.getDay().getShifts()) : "",
                            millArea != null ? String.valueOf(millArea.getMtd().getShifts()) : "",
                            normArea != null ? String.valueOf(normArea.getDay().getShifts()) : "",
                            normArea != null ? String.valueOf(normArea.getMtd().getShifts()) : ""},
            };

            Set<Integer> totalRowIndices = new HashSet<>(Arrays.asList(9, 10, 11));

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
            case "planned": return m.getPlanned();
            case "electrical": return m.getElectrical();
            case "mechanical": return m.getMechanical();
            case "operation": return m.getOperation();
            case "sbs": return m.getSbs();
            case "fuelEmd": return m.getFuelEmd();
            case "power": return m.getPower();
            case "msds": return m.getMsds();
            case "others": return m.getOthers();
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

        Map<String, Integer> groupCounts = new LinkedHashMap<>();
        Map<String, Integer> groupDurations = new LinkedHashMap<>();
        String[] groups = {"Planned", "Electrical", "Mechanical", "Operation", "SBS", "Fuel/EMD", "Power", "MSDS", "Others"};
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

