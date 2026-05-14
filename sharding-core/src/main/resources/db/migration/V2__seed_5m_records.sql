INSERT INTO shard_indices (object_id, shard_index)
SELECT
    'obj-' || LPAD(generate_series::text, 10, '0'),
    floor(random() * 64)::int
FROM generate_series(1, 5000000)
ON CONFLICT (object_id) DO NOTHING;
