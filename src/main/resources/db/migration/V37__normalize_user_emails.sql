-- Lowercase emails so login lookups (which normalize to lowercase) always match.
-- Collision-safe: rows whose lowercase form is shared with ANOTHER row are left
-- untouched (lowering both would violate uq_users_email); review them manually.
UPDATE users
SET email = lower(email)
WHERE email <> lower(email)
  AND NOT EXISTS (SELECT 1 FROM users u2
                  WHERE u2.id <> users.id
                    AND lower(u2.email) = lower(users.email));

UPDATE students
SET email = lower(email)
WHERE email IS NOT NULL
  AND email <> lower(email)
  AND NOT EXISTS (SELECT 1 FROM students s2
                  WHERE s2.id <> students.id
                    AND lower(s2.email) = lower(students.email));
