package ru.sfedu.sharding.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ru.sfedu.sharding.service.entity.ShardIndex;

@Repository
public interface ShardIndexRepository extends JpaRepository<ShardIndex, Long> {

    Optional<ShardIndex> findByObjectId(String objectId);

    boolean existsByObjectId(String objectId);
}
