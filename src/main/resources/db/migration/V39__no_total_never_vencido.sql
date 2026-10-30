-- Sin total acordado no hay deuda que cobrar.
-- EnrollmentExpirationJob marcaba VENCIDO a toda matrícula vencida, sin mirar si se
-- había acordado un total, y Finanzas cuenta VENCIDO como deuda: esas filas inflaban
-- el KPI de deuda. Se corrigen solo las filas sin total acordado, pasándolas a
-- PENDIENTE — que es exactamente lo que devuelve derivePaymentStatus para ellas.
-- Idempotente: al re-ejecutar no coincide ninguna fila, porque las que toca ya no
-- están en VENCIDO.
UPDATE enrollments
SET payment_status = 'PENDIENTE', updated_at = NOW()
WHERE payment_status = 'VENCIDO'
  AND (total_amount IS NULL OR total_amount <= 0);
