package ru.sfedu.sharding.service.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import ru.sfedu.sharding.service.dto.ShardIndexResponse;
import ru.sfedu.sharding.service.entity.ShardIndex;
import ru.sfedu.sharding.service.exception.ShardUpdateConflictException;
import ru.sfedu.sharding.service.repository.ShardIndexRepository;
import ru.sfedu.sharding.service.service.ShardingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ShardingServiceConcurrencyTest {

    private static final String TEST_OBJECT_ID = "concurrent-test-obj";
    private static final int THREAD_COUNT = 10;

    @Autowired
    private ShardingService shardingService;

    @Autowired
    private ShardIndexRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
        repository.save(new ShardIndex(TEST_OBJECT_ID, 0));
        repository.flush();
    }

    @Test
    void concurrentUpdates_shouldNotLoseUpdates() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            int targetShard = i + 1;
            futures.add(executor.submit(new Callable<Integer>() {
                @Override
                public Integer call() {
                    try {
                        ShardIndexResponse response = shardingService.updateShardIndex(TEST_OBJECT_ID, targetShard);
                        return response.shardIndex();
                    } catch (ShardUpdateConflictException e) {
                        return -1;
                    }
                }
            }));
        }

        executor.shutdown();

        int conflictCount = 0;
        for (Future<Integer> future : futures) {
            int result = future.get();
            if (result < 0) {
                conflictCount++;
            }
        }

        ShardIndex finalEntity = repository.findByObjectId(TEST_OBJECT_ID).orElseThrow();
        ShardIndexResponse finalResponse = shardingService.getShardIndex(TEST_OBJECT_ID);

        assertNotNull(finalEntity);
        assertEquals(finalEntity.getShardIndex(), finalResponse.shardIndex());
        assertTrue(finalEntity.getShardIndex() >= 1 && finalEntity.getShardIndex() <= THREAD_COUNT,
                "Final shard index should be one of the target values, but was: "
                        + finalEntity.getShardIndex());
        assertTrue(conflictCount <= THREAD_COUNT - 1,
                "At least one update should succeed, conflicts: " + conflictCount);
    }

    @Test
    void concurrentReads_shouldNotBlock() throws InterruptedException, ExecutionException {
        repository.save(new ShardIndex("read-test-obj", 42));
        repository.flush();

        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<ShardIndexResponse>> futures = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            futures.add(executor.submit(() -> shardingService.getShardIndex("read-test-obj")));
        }

        executor.shutdown();

        for (Future<ShardIndexResponse> future : futures) {
            ShardIndexResponse response = future.get();
            assertNotNull(response);
            assertEquals("read-test-obj", response.objectId());
            assertEquals(42, response.shardIndex());
        }
    }

    @Test
    void createAndUpdateSequence_shouldWorkUnderLoad() throws InterruptedException, ExecutionException {
        String objectId = "sequence-test-obj";

        shardingService.createShardIndex(objectId, 1);

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            int value = 10 + i;
            futures.add(executor.submit(() -> {
                try {
                    ShardIndexResponse response = shardingService.updateShardIndex(objectId, value);
                    return response.shardIndex();
                } catch (ShardUpdateConflictException e) {
                    return -1;
                }
            }));
        }

        executor.shutdown();

        int successCount = 0;
        for (Future<Integer> future : futures) {
            if (future.get() >= 0) {
                successCount++;
            }
        }

        assertTrue(successCount >= 1, "At least one update should succeed");
        ShardIndexResponse finalResponse = shardingService.getShardIndex(objectId);
        assertTrue(finalResponse.shardIndex() >= 10);
    }
}
