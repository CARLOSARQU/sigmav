-- DNI para docentes y admins (tabla users)
ALTER TABLE users ADD COLUMN IF NOT EXISTS dni VARCHAR(12);

-- Foto de perfil para docentes y admins
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512);

-- Foto de perfil para estudiantes (tabla propia)
ALTER TABLE students ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512);
