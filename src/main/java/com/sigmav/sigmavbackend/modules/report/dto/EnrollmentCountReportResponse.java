package com.sigmav.sigmavbackend.modules.report.dto;

import java.util.List;

public record EnrollmentCountReportResponse(
        long total,
        List<Item> breakdown
) {
    public record Item(
            Long id,
            String name,
            String type,   // AREA | COURSE
            long count
    ) {}
}
