-- Vencimiento al final del día, no a medianoche.
-- Las matrículas guardaban una fecha sin hora como 00:00:00, y las consultas de
-- acceso exigen expires_at > NOW(). Resultado: "vence el 30/09" cortaba el acceso
-- al empezar el día 30, quitándole al alumno ese día completo.
-- Solo se tocan las filas que están exactamente a medianoche; las que ya tienen
-- hora propia se respetan.
UPDATE enrollments
SET expires_at = date_trunc('day', expires_at) + interval '23 hours 59 minutes 59 seconds',
    updated_at = NOW()
WHERE expires_at IS NOT NULL
  AND expires_at = date_trunc('day', expires_at);

-- Reparación histórica del estado de pago.
-- EnrollmentExpirationJob marcaba VENCIDO a toda matrícula vencida sin mirar cuánto
-- se había pagado, y Finanzas cuenta VENCIDO como deuda: las matrículas pagadas por
-- completo inflaban el KPI de deuda. Se corrigen solo las filas demostrablemente
-- pagadas por completo; ninguna otra fila se toca.
UPDATE enrollments
SET payment_status = 'PAGADO', updated_at = NOW()
WHERE payment_status = 'VENCIDO'
  AND total_amount IS NOT NULL
  AND total_amount > 0
  AND amount_paid IS NOT NULL
  AND amount_paid >= total_amount;
