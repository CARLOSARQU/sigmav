-- V16__add_focus_lost_count_to_exam_attempts.sql
-- Contador de veces que el estudiante salió de la pantalla durante un examen (anti-cheat)

ALTER TABLE exam_attempts ADD COLUMN focus_lost_count INT NOT NULL DEFAULT 0;
