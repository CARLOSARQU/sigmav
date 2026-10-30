-- =============================================================================
-- V33: Add minimum_score to simulacro_careers for cut-off comparison
-- =============================================================================

-- 1. Add minimum_score column (nullable — not all careers have a defined cut-off)
ALTER TABLE simulacro_careers
ADD COLUMN IF NOT EXISTS minimum_score DECIMAL(5,2) DEFAULT NULL;

-- 2. Update seed data with sample cut-off scores for existing careers
-- UNI
UPDATE simulacro_careers SET minimum_score = 75.00 WHERE name = 'Ingeniería de Sistemas';
UPDATE simulacro_careers SET minimum_score = 65.00 WHERE name = 'Ingeniería Civil';
UPDATE simulacro_careers SET minimum_score = 60.00 WHERE name = 'Ingeniería Industrial';

-- San Marcos
UPDATE simulacro_careers SET minimum_score = 80.00 WHERE name = 'Medicina Humana';
UPDATE simulacro_careers SET minimum_score = 55.00 WHERE name = 'Enfermería';
UPDATE simulacro_careers SET minimum_score = 70.00 WHERE name = 'Derecho';

-- PUCP
UPDATE simulacro_careers SET minimum_score = 72.00 WHERE name = 'Ingeniería Informática';
UPDATE simulacro_careers SET minimum_score = 68.00 WHERE name = 'Administración';

-- Pacífico
UPDATE simulacro_careers SET minimum_score = 70.00 WHERE name = 'Economía';
UPDATE simulacro_careers SET minimum_score = 65.00 WHERE name = 'Contabilidad';
