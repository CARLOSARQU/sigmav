-- V31__seed_simulacro_data.sql
-- Seeds sample simulacro hierarchy data for every academy.
-- Uses INSERT ... SELECT so it works for any existing academies.
-- Idempotent: each INSERT uses WHERE NOT EXISTS to skip already-seeded rows.

-- ============================================================
-- 1. Universities
-- ============================================================
INSERT INTO simulacro_universities (academy_id, name, description)
SELECT a.id, 'Universidad Nacional de Ingeniería', 'Prestigiosa universidad pública especializada en ingeniería, ciencias y arquitectura'
FROM academies a
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_universities su WHERE su.academy_id = a.id AND su.name = 'Universidad Nacional de Ingeniería'
);

INSERT INTO simulacro_universities (academy_id, name, description)
SELECT a.id, 'Universidad Nacional Mayor de San Marcos', 'Decana de América, universidad pública con más de 400 años de historia'
FROM academies a
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_universities su WHERE su.academy_id = a.id AND su.name = 'Universidad Nacional Mayor de San Marcos'
);

INSERT INTO simulacro_universities (academy_id, name, description)
SELECT a.id, 'Pontificia Universidad Católica del Perú', 'Universidad privada líder en ciencias sociales, humanidades y derecho'
FROM academies a
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_universities su WHERE su.academy_id = a.id AND su.name = 'Pontificia Universidad Católica del Perú'
);

INSERT INTO simulacro_universities (academy_id, name, description)
SELECT a.id, 'Universidad del Pacífico', 'Universidad privada especializada en negocios, economía y finanzas'
FROM academies a
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_universities su WHERE su.academy_id = a.id AND su.name = 'Universidad del Pacífico'
);

-- ============================================================
-- 2. Areas
-- ============================================================

-- UNI areas
INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Ciencias Básicas', 'Matemáticas, física y química para ingeniería'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional de Ingeniería'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Ciencias Básicas'
);

INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Ingeniería Civil', 'Estructuras, geotecnia, hidráulica y construcción civil'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional de Ingeniería'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Ingeniería Civil'
);

INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Ingeniería de Sistemas', 'Computación, software, redes e inteligencia artificial'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional de Ingeniería'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Ingeniería de Sistemas'
);

-- San Marcos areas
INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Ciencias de la Salud', 'Medicina, enfermería, obstetricia y ciencias de la salud'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional Mayor de San Marcos'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Ciencias de la Salud'
);

INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Ciencias Sociales', 'Sociología, antropología, historia y trabajo social'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional Mayor de San Marcos'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Ciencias Sociales'
);

INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Ingeniería Industrial', 'Gestión de operaciones, producción y logística'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional Mayor de San Marcos'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Ingeniería Industrial'
);

-- PUCP areas
INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Derecho', 'Ciencias jurídicas y formación legal integral'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Pontificia Universidad Católica del Perú'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Derecho'
);

INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Administración y Negocios', 'Gestión empresarial, marketing y recursos humanos'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Pontificia Universidad Católica del Perú'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Administración y Negocios'
);

INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Arquitectura', 'Diseño urbano, planificación y construcción sostenible'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Pontificia Universidad Católica del Perú'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Arquitectura'
);

-- Universidad del Pacífico areas
INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Economía', 'Teoría económica, econometría y políticas públicas'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad del Pacífico'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Economía'
);

INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Finanzas', 'Gestión financiera, inversiones y banca'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad del Pacífico'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Finanzas'
);

INSERT INTO simulacro_areas (academy_id, university_id, name, description)
SELECT a.id, su.id, 'Contabilidad', 'Contabilidad financiera, auditoría y tributación'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad del Pacífico'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_areas sa WHERE sa.university_id = su.id AND sa.name = 'Contabilidad'
);

-- ============================================================
-- 3. Careers
-- ============================================================

-- UNI -> Ciencias Básicas
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Matemática', 'Matemáticas puras y aplicadas'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional de Ingeniería'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ciencias Básicas'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Matemática'
);

INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Física', 'Física teórica y experimental'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional de Ingeniería'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ciencias Básicas'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Física'
);

INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Química', 'Química pura y aplicada'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional de Ingeniería'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ciencias Básicas'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Química'
);

-- UNI -> Ingeniería Civil
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Ingeniería Civil', 'Estructuras, geotecnia y construcción'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional de Ingeniería'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ingeniería Civil'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Ingeniería Civil'
);

-- UNI -> Ingeniería de Sistemas
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Ingeniería de Sistemas', 'Computación y tecnologías de la información'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional de Ingeniería'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ingeniería de Sistemas'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Ingeniería de Sistemas'
);

INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Ingeniería de Software', 'Desarrollo de software, calidad y arquitectura'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional de Ingeniería'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ingeniería de Sistemas'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Ingeniería de Software'
);

INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Ciencia de la Computación', 'Algoritmos, inteligencia artificial y datos'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional de Ingeniería'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ingeniería de Sistemas'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Ciencia de la Computación'
);

-- San Marcos -> Ciencias de la Salud
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Medicina Humana', 'Medicina general y especialidades clínicas'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional Mayor de San Marcos'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ciencias de la Salud'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Medicina Humana'
);

INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Enfermería', 'Cuidado integral de la salud y gestión del cuidado'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional Mayor de San Marcos'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ciencias de la Salud'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Enfermería'
);

INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Obstetricia', 'Salud materna, perinatal y planificación familiar'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional Mayor de San Marcos'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ciencias de la Salud'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Obstetricia'
);

-- San Marcos -> Ciencias Sociales
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Sociología', 'Estudio de la sociedad y fenómenos sociales'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional Mayor de San Marcos'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ciencias Sociales'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Sociología'
);

INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Historia', 'Historia universal, del Perú y metodología histórica'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional Mayor de San Marcos'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ciencias Sociales'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Historia'
);

-- San Marcos -> Ingeniería Industrial
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Ingeniería Industrial', 'Gestión de operaciones, calidad y productividad'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad Nacional Mayor de San Marcos'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Ingeniería Industrial'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Ingeniería Industrial'
);

-- PUCP -> Derecho
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Derecho', 'Formación integral en ciencias jurídicas'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Pontificia Universidad Católica del Perú'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Derecho'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Derecho'
);

-- PUCP -> Administración y Negocios
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Administración de Empresas', 'Gestión empresarial y liderazgo organizacional'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Pontificia Universidad Católica del Perú'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Administración y Negocios'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Administración de Empresas'
);

INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Marketing', 'Estrategias comerciales, investigación de mercados y publicidad'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Pontificia Universidad Católica del Perú'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Administración y Negocios'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Marketing'
);

-- PUCP -> Arquitectura
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Arquitectura', 'Diseño arquitectónico, urbanismo y planificación territorial'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Pontificia Universidad Católica del Perú'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Arquitectura'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Arquitectura'
);

-- Universidad del Pacífico -> Economía
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Economía', 'Análisis económico, políticas públicas y finanzas'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad del Pacífico'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Economía'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Economía'
);

-- Universidad del Pacífico -> Finanzas
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Finanzas', 'Gestión de inversiones, banca y mercados financieros'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad del Pacífico'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Finanzas'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Finanzas'
);

-- Universidad del Pacífico -> Contabilidad
INSERT INTO simulacro_careers (academy_id, area_id, name, description)
SELECT a.id, sa.id, 'Contabilidad', 'Contabilidad financiera, auditoría y tributación'
FROM academies a
JOIN simulacro_universities su ON su.academy_id = a.id AND su.name = 'Universidad del Pacífico'
JOIN simulacro_areas sa ON sa.university_id = su.id AND sa.name = 'Contabilidad'
WHERE NOT EXISTS (
    SELECT 1 FROM simulacro_careers sc WHERE sc.area_id = sa.id AND sc.name = 'Contabilidad'
);
