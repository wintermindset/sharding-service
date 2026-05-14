package ru.sfedu.sharding.service.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import ru.sfedu.sharding.service.dto.ShardIndexResponse;
import ru.sfedu.sharding.service.entity.ShardIndex;
import ru.sfedu.sharding.service.exception.ShardIndexAlreadyExistsException;
import ru.sfedu.sharding.service.exception.ShardIndexNotFoundException;
import ru.sfedu.sharding.service.exception.ShardUpdateConflictException;
import ru.sfedu.sharding.service.repository.ShardIndexRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShardingServiceTest {

    @Mock
    private ShardIndexRepository repository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ShardingService service;

    @BeforeEach
    void setUp() {
        service = new ShardingService(repository, transactionTemplate);
    }

    @Test
    void createShardIndex_shouldReturnResponse() {
        String objectId = "obj-0000000001";
        int shardIndex = 5;
        ShardIndex entity = new ShardIndex(objectId, shardIndex);
        entity.setId(1L);

        when(repository.existsByObjectId(objectId)).thenReturn(false);
        when(repository.save(any(ShardIndex.class))).thenReturn(entity);

        ShardIndexResponse response = service.createShardIndex(objectId, shardIndex);

        assertNotNull(response);
        assertEquals(objectId, response.objectId());
        assertEquals(shardIndex, response.shardIndex());
        verify(repository).existsByObjectId(objectId);
        verify(repository).save(any(ShardIndex.class));
    }

    @Test
    void createShardIndex_shouldThrowWhenExists() {
        String objectId = "obj-0000000001";
        when(repository.existsByObjectId(objectId)).thenReturn(true);

        assertThrows(ShardIndexAlreadyExistsException.class,
                () -> service.createShardIndex(objectId, 5));
        verify(repository, never()).save(any());
    }

    @Test
    void getShardIndex_shouldReturnResponse() {
        String objectId = "obj-0000000001";
        int shardIndex = 5;
        ShardIndex entity = new ShardIndex(objectId, shardIndex);

        when(repository.findByObjectId(objectId)).thenReturn(Optional.of(entity));

        ShardIndexResponse response = service.getShardIndex(objectId);

        assertNotNull(response);
        assertEquals(objectId, response.objectId());
        assertEquals(shardIndex, response.shardIndex());
    }

    @Test
    void getShardIndex_shouldThrowWhenNotFound() {
        String objectId = "nonexistent";
        when(repository.findByObjectId(objectId)).thenReturn(Optional.empty());

        assertThrows(ShardIndexNotFoundException.class,
                () -> service.getShardIndex(objectId));
    }

    @Test
    void updateShardIndex_shouldReturnResponse() {
        String objectId = "obj-0000000001";
        int oldIndex = 5;
        int newIndex = 10;
        ShardIndex entity = new ShardIndex(objectId, oldIndex);
        entity.setVersion(0L);

        when(repository.findByObjectId(objectId)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(ShardIndex.class))).thenAnswer(invocation -> {
            ShardIndex saved = invocation.getArgument(0);
            saved.setVersion(1L);
            return saved;
        });
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback<ShardIndexResponse> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });

        ShardIndexResponse response = service.updateShardIndex(objectId, newIndex);

        assertNotNull(response);
        assertEquals(objectId, response.objectId());
        assertEquals(newIndex, response.shardIndex());
    }

    @Test
    void updateShardIndex_shouldThrowWhenNotFound() {
        String objectId = "nonexistent";
        when(repository.findByObjectId(objectId)).thenReturn(Optional.empty());
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback<ShardIndexResponse> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });

        assertThrows(ShardIndexNotFoundException.class,
                () -> service.updateShardIndex(objectId, 10));
    }

    @Test
    void updateShardIndex_shouldRetryOnOptimisticLock() {
        String objectId = "obj-0000000001";
        ShardIndex entity = new ShardIndex(objectId, 5);
        entity.setVersion(0L);

        when(repository.findByObjectId(objectId)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(ShardIndex.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenAnswer(invocation -> {
                    ShardIndex saved = invocation.getArgument(0);
                    saved.setVersion(1L);
                    return saved;
                });
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback<ShardIndexResponse> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                })
                .thenAnswer(invocation -> {
                    TransactionCallback<ShardIndexResponse> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });

        ShardIndexResponse response = service.updateShardIndex(objectId, 10);

        assertNotNull(response);
        assertEquals(10, response.shardIndex());
        verify(repository, times(2)).saveAndFlush(any(ShardIndex.class));
        verify(transactionTemplate, times(2)).execute(any(TransactionCallback.class));
    }

    @Test
    void updateShardIndex_shouldThrowAfterMaxRetries() {
        String objectId = "obj-0000000001";
        ShardIndex entity = new ShardIndex(objectId, 5);
        entity.setVersion(0L);

        when(repository.findByObjectId(objectId)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(ShardIndex.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"));
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback<ShardIndexResponse> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });

        assertThrows(ShardUpdateConflictException.class,
                () -> service.updateShardIndex(objectId, 10));
        verify(repository, times(3)).saveAndFlush(any(ShardIndex.class));
        verify(transactionTemplate, times(3)).execute(any(TransactionCallback.class));
    }
}
