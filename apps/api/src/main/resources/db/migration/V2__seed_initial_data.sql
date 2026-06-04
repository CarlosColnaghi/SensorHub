INSERT INTO users (name, email)
VALUES ('admin', 'admin@sensorhub.com')
ON CONFLICT (email) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

INSERT INTO devices (user_uuid, hardware_uuid, name, environment_uuid, status)
SELECT uuid,
       'b0fee3a6-ae91-4265-9365-36f793f32f06'::uuid,
       'Admin seed sensor',
       NULL,
       'ACTIVATED'
FROM users
WHERE email = 'admin@sensorhub.com'
ON CONFLICT (hardware_uuid) DO UPDATE
SET user_uuid = EXCLUDED.user_uuid,
    name = EXCLUDED.name,
    environment_uuid = NULL,
    status = EXCLUDED.status,
    updated_at = now();
