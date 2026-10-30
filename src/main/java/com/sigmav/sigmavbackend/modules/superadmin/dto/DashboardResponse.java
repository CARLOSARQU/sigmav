package com.sigmav.sigmavbackend.modules.superadmin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        long totalAcademies,
        long activeAcademies,
        long totalStudents,
        BigDecimal monthlyRevenue,
        BigDecimal totalRevenue,
        long expiringThisWeek,
        Map<String, Long> planDistribution,
        long examsRenderedToday,
        List<MonthlyRevenuePoint> monthlyRevenueChart,  // últimos 6 meses
        List<RecentAcademyItem> recentAcademies          // últimas 5 academias creadas
) {}
