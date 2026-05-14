package ru.sfedu.sharding.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shard_indices")
@Getter
@Setter
@NoArgsConstructor
public class ShardIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_id", unique = true, nullable = false, length = 64)
    private String objectId;

    @Column(name = "shard_index", nullable = false)
    private Integer shardIndex;

    @Version
    @Column(name = "version")
    private Long version;

    public ShardIndex(String objectId, Integer shardIndex) {
        this.objectId = objectId;
        this.shardIndex = shardIndex;
    }
}
