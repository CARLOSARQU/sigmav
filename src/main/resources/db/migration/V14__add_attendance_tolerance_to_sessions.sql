-- V14: Añade tolerancia de tardanza configurable por sesión
-- null = usar default de academia (academy.config.attendanceTolerance, default 10 min)
ALTER TABLE class_sessions
    ADD COLUMN attendance_tolerance INTEGER;
