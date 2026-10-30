package com.sigmav.sigmavbackend.modules.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminDashboardResponse(
        // existing
        long activeStudents,
        long activeCourses,
        long enrollmentsThisMonth,
        BigDecimal revenueThisMonth,
        long upcomingExams,
        double attendanceRateAvg,
        // financial additions
        BigDecimal revenueLastMonth,
        BigDecimal pendingCollections,
        long enrollmentsPagado,
        long enrollmentsParcial,
        long enrollmentsPendiente,
        long enrollmentsVencido,
        List<MonthlyRevenue> monthlyRevenueSeries
) {
    public record MonthlyRevenue(String month, BigDecimal revenue, long enrollmentCount) {}
}
