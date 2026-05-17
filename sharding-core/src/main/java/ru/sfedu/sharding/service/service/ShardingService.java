package ru.sfedu.sharding.service.service;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.sfedu.sharding.service.dto.ShardIndexResponse;
import ru.sfedu.sharding.service.entity.ShardIndex;
import ru.sfedu.sharding.service.exception.ShardIndexAlreadyExistsException;
import ru.sfedu.sharding.service.exception.ShardIndexNotFoundException;
import ru.sfedu.sharding.service.exception.ShardUpdateConflictException;
import ru.sfedu.sharding.service.repository.ShardIndexRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShardingService {

    private static final int MAX_RETRIES = 3;

    private final ShardIndexRepository repository;
    private final TransactionTemplate transactionTemplate;

    public ShardIndexResponse createShardIndex(String objectId, Integer shardIndex) {
        if (repository.existsByObjectId(objectId)) {
            log.warn("create object={} already exists", objectId);
            throw new ShardIndexAlreadyExistsException(objectId);
        }
        ShardIndex entity = new ShardIndex(objectId, shardIndex);
        entity = repository.save(entity);
        ShardIndexResponse response = ShardIndexResponse.of(entity.getObjectId(), entity.getShardIndex());
        log.info("create object={} shard={} success", objectId, shardIndex);
        return response;
    }

    public ShardIndexResponse updateShardIndex(String objectId, Integer newShardIndex) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            int currentAttempt = attempt;
            try {
                return transactionTemplate.execute(status -> {
                    ShardIndex entity = repository.findByObjectId(objectId)
                        .orElseThrow(() -> new ShardIndexNotFoundException(objectId));
                    int oldShardIndex = entity.getShardIndex();
                    entity.setShardIndex(newShardIndex);
                    entity = repository.save(entity);
                    log.info("update object={} shard={}->{} attempt={}/{}",
                        objectId, oldShardIndex, newShardIndex, currentAttempt, MAX_RETRIES);
                    return ShardIndexResponse.of(entity.getObjectId(), entity.getShardIndex());
                });
            } catch (OptimisticLockingFailureException e) {
                log.warn("update object={} optimistic lock conflict attempt={}/{}",
                    objectId, currentAttempt, MAX_RETRIES);
                if (currentAttempt == MAX_RETRIES) {
                    log.error("update object={} exhausted retries", objectId);
                    throw new ShardUpdateConflictException(objectId);
                }
            }
        }
        log.error("update object={} exhausted retries", objectId);
        throw new ShardUpdateConflictException(objectId);
    }

    public ShardIndexResponse getShardIndex(String objectId) {
        ShardIndex entity = repository.findByObjectId(objectId)
            .orElseThrow(() -> new ShardIndexNotFoundException(objectId));
        log.debug("read object={} shard={}", objectId, entity.getShardIndex());
        return ShardIndexResponse.of(entity.getObjectId(), entity.getShardIndex());
    }
}
