-- Enrollments: active course enrollment lookup (existsActiveCourseEnrollment — hot path)
CREATE INDEX IF NOT EXISTS idx_enrollments_active_course
    ON enrollments(student_id, enrollment_type, is_active)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_enrollments_active_course_id
    ON enrollments(student_id, course_id, is_active)
    WHERE enrollment_type = 'COURSE' AND is_active = true;

CREATE INDEX IF NOT EXISTS idx_enrollments_active_area_id
    ON enrollments(student_id, area_id, is_active)
    WHERE enrollment_type = 'AREA' AND is_active = true;

-- Exam attempts: exam+student composite (countByExamIdAndStudentId — hot path on exam start)
CREATE INDEX IF NOT EXISTS idx_exam_attempts_exam_student
    ON exam_attempts(exam_id, student_id);

-- Attendance: academy+status for dashboard aggregation
CREATE INDEX IF NOT EXISTS idx_attendance_academy_status
    ON attendance_records(academy_id, status);

-- Enrollments: academy+enrolled_at for revenue and count period queries
CREATE INDEX IF NOT EXISTS idx_enrollments_academy_enrolled_at
    ON enrollments(academy_id, enrolled_at);

-- Students: academy+active for dashboard count
CREATE INDEX IF NOT EXISTS idx_students_academy_active
    ON students(academy_id, is_active)
    WHERE is_active = true;

-- Sessions: academy+active+date for calendar queries
CREATE INDEX IF NOT EXISTS idx_sessions_academy_active_date
    ON class_sessions(academy_id, is_active, session_date)
    WHERE is_active = true;
