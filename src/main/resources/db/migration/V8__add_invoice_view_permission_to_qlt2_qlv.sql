-- Migration V8: Add INVOICE_VIEW permission to QLT2 and QLV roles

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'INVOICE_VIEW'
FROM roles
WHERE UPPER(code) = 'QLT2'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = roles.id AND rp.permission = 'INVOICE_VIEW'
  );
