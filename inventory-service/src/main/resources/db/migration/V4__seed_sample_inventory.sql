INSERT INTO inventory (product_id, available_qty, reserved_qty, version)
VALUES
    (1, 100, 0, 0),
    (2, 100, 0, 0),
    (3, 100, 0, 0)
ON CONFLICT (product_id) DO NOTHING;
