CREATE UNIQUE INDEX uq_one_admin_per_company ON users (company_id, role) WHERE role = 'COMPANY_ADMIN';
