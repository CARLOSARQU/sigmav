-- V15__add_payment_method_to_enrollments.sql
-- Agrega método de pago a la tabla de matrículas

ALTER TABLE enrollments ADD COLUMN payment_method VARCHAR(50);
