package com.sigmav.sigmavbackend.modules.teacher.dto;

import java.time.LocalDateTime;

public record CourseTeacherResponse(
        Long id,
        Long courseId,
        Long teacherId,
        String teacherFullName,
        String courseName,
        Boolean canCreateExams,
        LocalDateTime assignedAt
) {}
