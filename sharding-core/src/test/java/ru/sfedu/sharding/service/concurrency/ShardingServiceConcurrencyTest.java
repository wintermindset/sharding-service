package ru.sfedu.sharding.service.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    void concurrentUpdates_shouldUseOptimisticLocking()
            throws InterruptedException, ExecutionException {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            int targetShard = i + 1;
            Callable<Integer> task = () -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    ShardIndexResponse response =
                            shardingService.updateShardIndex(TEST_OBJECT_ID, targetShard);
                    return response.shardIndex();
                } catch (ShardUpdateConflictException e) {
                    return -1;
                }
            };
            futures.add(executor.submit(task));
        }
        readyLatch.await();
        startLatch.countDown();
        int successCount = 0;
        int conflictCount = 0;
        for (Future<Integer> future : futures) {
            int result = future.get();
            if (result >= 0) {
                successCount++;
            } else {
                conflictCount++;
            }
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        ShardIndex finalEntity =
                repository.findByObjectId(TEST_OBJECT_ID).orElseThrow();
        assertNotNull(finalEntity);
        assertTrue(
                finalEntity.getShardIndex() >= 1
                        && finalEntity.getShardIndex() <= THREAD_COUNT
        );
        assertTrue(successCount >= 1);
        assertTrue(conflictCount >= 1);
        assertEquals(
                THREAD_COUNT,
                successCount + conflictCount
        );
        assertNotNull(finalEntity.getVersion());
        assertTrue(finalEntity.getVersion() > 0);
    }

    @Test
    void concurrentReads_shouldReturnConsistentData()
            throws InterruptedException, ExecutionException {

        String objectId = "read-test-obj";
        repository.save(new ShardIndex(objectId, 42));
        repository.flush();
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<ShardIndexResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(
                    executor.submit(
                            () -> shardingService.getShardIndex(objectId)
                    )
            );
        }
        for (Future<ShardIndexResponse> future : futures) {
            ShardIndexResponse response = future.get();
            assertNotNull(response);
            assertEquals(objectId, response.objectId());
            assertEquals(42, response.shardIndex());
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void createThenConcurrentUpdate_shouldRemainConsistent()
            throws InterruptedException, ExecutionException {

        String objectId = "sequence-test-obj";
        shardingService.createShardIndex(objectId, 1);
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int targetValue = 10 + i;
            futures.add(
                    executor.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();
                        try {
                            return shardingService
                                    .updateShardIndex(objectId, targetValue)
                                    .shardIndex();
                        } catch (ShardUpdateConflictException e) {
                            return -1;
                        }
                    })
            );
        }

        readyLatch.await();
        startLatch.countDown();

        int successCount = 0;
        for (Future<Integer> future : futures) {
            if (future.get() >= 0) {
                successCount++;
            }
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        ShardIndex entity =
                repository.findByObjectId(objectId).orElseThrow();
        assertTrue(successCount >= 1);
        assertTrue(
                entity.getShardIndex() >= 10
                        && entity.getShardIndex() <= 14
        );
        assertNotNull(entity.getVersion());
        assertTrue(entity.getVersion() > 0);
    }
}