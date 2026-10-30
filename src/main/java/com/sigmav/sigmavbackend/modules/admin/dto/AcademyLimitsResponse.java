package com.sigmav.sigmavbackend.modules.admin.dto;

public record AcademyLimitsResponse(
        int maxCourses,
        int maxStudents,
        long activeCourses,
        long activeStudents,
        String reportScheduleDay,
        Integer reportScheduleHour
) {}
