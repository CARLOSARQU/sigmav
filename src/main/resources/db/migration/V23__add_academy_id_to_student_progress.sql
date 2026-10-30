ALTER TABLE student_progress ADD COLUMN academy_id BIGINT REFERENCES academies(id);

UPDATE student_progress sp
SET academy_id = s.academy_id
FROM students s
WHERE sp.student_id = s.id;

ALTER TABLE student_progress ALTER COLUMN academy_id SET NOT NULL;

CREATE INDEX idx_student_progress_academy ON student_progress(academy_id);
