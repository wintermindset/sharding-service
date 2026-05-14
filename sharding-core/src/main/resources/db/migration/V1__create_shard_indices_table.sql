CREATE TABLE IF NOT EXISTS shard_indices (
    id          BIGSERIAL    PRIMARY KEY,
    object_id   VARCHAR(64)  NOT NULL,
    shard_index INTEGER      NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_shard_indices_object_id ON shard_indices (object_id);

CREATE INDEX IF NOT EXISTS idx_shard_indices_shard_index ON shard_indices (shard_index);
