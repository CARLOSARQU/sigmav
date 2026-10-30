package com.sigmav.sigmavbackend.modules.superadmin.dto;

import java.math.BigDecimal;

public record MonthlyRevenuePoint(
        String month,    // formato "yyyy-MM", ej: "2026-01"
        BigDecimal revenue
) {}
