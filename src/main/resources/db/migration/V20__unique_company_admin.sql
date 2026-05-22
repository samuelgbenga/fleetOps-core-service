-- Enforce one COMPANY_ADMIN per company at the database level.
-- A partial index is used so the uniqueness constraint only applies to admin rows,
-- leaving other roles (DRIVER, MECHANIC, etc.) unaffected.
CREATE UNIQUE INDEX uq_company_admin
    ON users (company_id)
    WHERE role = 'COMPANY_ADMIN';
