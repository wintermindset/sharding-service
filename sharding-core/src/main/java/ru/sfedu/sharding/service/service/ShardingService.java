package ru.sfedu.sharding.service.service;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import ru.sfedu.sharding.service.dto.ShardIndexResponse;
import ru.sfedu.sharding.service.entity.ShardIndex;
import ru.sfedu.sharding.service.exception.ShardIndexAlreadyExistsException;
import ru.sfedu.sharding.service.exception.ShardIndexNotFoundException;
import ru.sfedu.sharding.service.exception.ShardUpdateConflictException;
import ru.sfedu.sharding.service.repository.ShardIndexRepository;

@Service
@RequiredArgsConstructor
public class ShardingService {

    private static final int MAX_RETRIES = 3;

    private final ShardIndexRepository repository;
    private final TransactionTemplate transactionTemplate;

    public ShardIndexResponse createShardIndex(String objectId, Integer shardIndex) {
        if (repository.existsByObjectId(objectId)) {
            throw new ShardIndexAlreadyExistsException(objectId);
        }
        ShardIndex entity = new ShardIndex(objectId, shardIndex);
        entity = repository.save(entity);
        return ShardIndexResponse.of(entity.getObjectId(), entity.getShardIndex());
    }

    public ShardIndexResponse updateShardIndex(String objectId, Integer newShardIndex) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status -> {
                    ShardIndex entity = repository.findByObjectId(objectId)
                        .orElseThrow(() -> new ShardIndexNotFoundException(objectId));
                    entity.setShardIndex(newShardIndex);
                    entity = repository.save(entity);
                    return ShardIndexResponse.of(entity.getObjectId(), entity.getShardIndex());
                });
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_RETRIES) {
                    throw new ShardUpdateConflictException(objectId);
                }
            }
        }
        throw new ShardUpdateConflictException(objectId);
    }

    public ShardIndexResponse getShardIndex(String objectId) {
        ShardIndex entity = repository.findByObjectId(objectId)
            .orElseThrow(() -> new ShardIndexNotFoundException(objectId));
        return ShardIndexResponse.of(entity.getObjectId(), entity.getShardIndex());
    }
}
