package com.sigmav.sigmavbackend.modules.academy.dto;

public record UpdateAcademyRequest(
        String name,
        String planType,
        Integer maxStudents,
        Integer maxCourses,
        Integer maxStorageMb,
        String reportScheduleDay,
        Integer reportScheduleHour
) {}
