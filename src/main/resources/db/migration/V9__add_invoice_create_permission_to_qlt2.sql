-- Migration V9: Add full Invoice management (CREATE, EDIT, DELETE, EXPORT) and Notification (VIEW, MANAGE) permissions to QLT2 role

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.perm
FROM roles r
CROSS JOIN (
    SELECT 'INVOICE_CREATE' AS perm
    UNION ALL SELECT 'INVOICE_EDIT'
    UNION ALL SELECT 'INVOICE_DELETE'
    UNION ALL SELECT 'INVOICE_EXPORT'
    UNION ALL SELECT 'NOTIFICATION_VIEW'
    UNION ALL SELECT 'NOTIFICATION_MANAGE'
) p
WHERE UPPER(r.code) = 'QLT2'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.perm
  );
