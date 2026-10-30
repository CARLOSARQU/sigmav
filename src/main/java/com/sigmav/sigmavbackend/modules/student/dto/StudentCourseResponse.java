package com.sigmav.sigmavbackend.modules.student.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Respuesta del portal de estudiante para GET /api/student/courses.
 *  Incluye la jerarquía ciclo → área → curso. */
public record StudentCourseResponse(
        Long courseId,
        UUID courseUuid,
        String courseName,
        String courseDescription,
        Long areaId,
        String areaName,
        Long cycleId,
        String cycleName,
        LocalDate cycleStartDate,
        LocalDate cycleEndDate,
        String enrollmentType   // AREA | COURSE
) {}
