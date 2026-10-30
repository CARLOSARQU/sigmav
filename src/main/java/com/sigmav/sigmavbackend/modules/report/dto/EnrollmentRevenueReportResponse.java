package com.sigmav.sigmavbackend.modules.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record EnrollmentRevenueReportResponse(
        BigDecimal totalRevenue,
        String currency,
        List<PeriodItem> breakdown
) {
    public record PeriodItem(
            String period,     // e.g. "2026-02"
            BigDecimal revenue,
            long enrollmentCount
    ) {}
}
