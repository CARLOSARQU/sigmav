package com.sigmav.sigmavbackend.modules.student.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record StudentCalendarResponse(
        List<SessionEvent> sessions,
        List<ExamEvent> exams
) {

    public record SessionEvent(
            Long id,
            String title,
            String courseName,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String meetingUrl
    ) {}

    public record ExamEvent(
            Long id,
            String title,
            String scopeType,
            Integer durationMinutes,
            LocalTime availableFrom,
            LocalTime availableUntil
    ) {}
}
