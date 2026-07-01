package com.bsp.delay.service;

import com.bsp.delay.dto.ReportResponse;
import java.io.IOException;
import java.time.LocalDate;

public interface ReportService {
    ReportResponse generateReport(Long shopId, Long areaId, String reportType, LocalDate date, String month, Integer year, LocalDate fromDate, LocalDate toDate, Long delayTypeId, String shift);
    byte[] generateExcelReport(Long shopId, Long areaId, String reportType, LocalDate date, String month, Integer year, LocalDate fromDate, LocalDate toDate, Long delayTypeId, String shift) throws IOException;
    byte[] generatePdfReport(Long shopId, Long areaId, String reportType, LocalDate date, String month, Integer year, LocalDate fromDate, LocalDate toDate, Long delayTypeId, String shift) throws IOException;
    String getPdfReportFilename(Long shopId, Long areaId, String reportType, LocalDate date, String month, Integer year, LocalDate fromDate, LocalDate toDate);
}
