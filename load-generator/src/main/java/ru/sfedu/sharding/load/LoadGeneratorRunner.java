package ru.sfedu.sharding.load;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class LoadGeneratorRunner implements CommandLineRunner {

    private static final int OBJECT_POOL_SIZE = 5_000_000;
    private static final int MIN_SHARD = 0;
    private static final int MAX_SHARD = 63;

    private final String targetUrl;
    private final long durationMinutes;
    private final HttpClient httpClient;
    private final StatsTracker stats = new StatsTracker();

    public LoadGeneratorRunner(
            @Value("${load.target.url:http://localhost:8080}") String targetUrl,
            @Value("${load.duration.minutes:15}") long durationMinutes) {
        this.targetUrl = targetUrl;
        this.durationMinutes = durationMinutes;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Load Generator Starting ===");
        System.out.println("Target: " + targetUrl);
        System.out.println("Duration: " + durationMinutes + " minutes");
        System.out.println("Creates: 1 TPS | Updates: 5 TPS | Reads: 10 TPS");
        System.out.println();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

        scheduler.scheduleAtFixedRate(
                () -> executeCreate(), 0, 1, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(
                () -> executeUpdate(), 0, 200, TimeUnit.MILLISECONDS);

        scheduler.scheduleAtFixedRate(
                () -> executeRead(), 0, 100, TimeUnit.MILLISECONDS);

        long intervalSec = 30;
        long totalIntervals = durationMinutes * 60 / intervalSec;
        for (int i = 0; i < totalIntervals; i++) {
            Thread.sleep(intervalSec * 1000);
            stats.printInterval();
        }

        scheduler.shutdown();
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
        }

        stats.printSummary(durationMinutes);
        System.out.println("=== Load Generator Finished ===");
    }

    private void executeCreate() {
        String objectId = "load-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        int shardIndex = randomShard();
        long start = System.nanoTime();
        try {
            String json = "{\"objectId\":\"" + objectId + "\",\"shardIndex\": " + shardIndex + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl + "/api/v1/shard-indices"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.nanoTime() - start;
            stats.record("create", elapsed, response.statusCode());
        } catch (Exception e) {
            long elapsed = System.nanoTime() - start;
            stats.recordError("create", elapsed);
        }
    }

    private void executeUpdate() {
        String objectId = randomExistingObject();
        int shardIndex = randomShard();
        long start = System.nanoTime();
        try {
            String json = "{\"shardIndex\": " + shardIndex + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl + "/api/v1/shard-indices/" + objectId))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.nanoTime() - start;
            stats.record("update", elapsed, response.statusCode());
        } catch (Exception e) {
            long elapsed = System.nanoTime() - start;
            stats.recordError("update", elapsed);
        }
    }

    private void executeRead() {
        String objectId = randomExistingObject();
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl + "/api/v1/shard-indices/" + objectId))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.nanoTime() - start;
            stats.record("read", elapsed, response.statusCode());
        } catch (Exception e) {
            long elapsed = System.nanoTime() - start;
            stats.recordError("read", elapsed);
        }
    }

    private int randomShard() {
        return MIN_SHARD + (int) (Math.random() * (MAX_SHARD - MIN_SHARD + 1));
    }

    private String randomExistingObject() {
        int id = 1 + (int) (Math.random() * OBJECT_POOL_SIZE);
        return "obj-" + String.format("%010d", id);
    }

    static class StatsTracker {
        private final Map<String, List<Long>> latencies = new ConcurrentHashMap<>();
        private final AtomicLong totalRequests = new AtomicLong();
        private final AtomicLong errorRequests = new AtomicLong();
        private final AtomicLong lastIntervalRequests = new AtomicLong();
        private final Object lock = new Object();
        private Instant lastIntervalTime = Instant.now();

        void record(String type, long elapsedNanos, int statusCode) {
            totalRequests.incrementAndGet();
            if (statusCode >= 400) {
                errorRequests.incrementAndGet();
            }
            latencies.computeIfAbsent(type, k -> new ArrayList<>()).add(elapsedNanos);
        }

        void recordError(String type, long elapsedNanos) {
            totalRequests.incrementAndGet();
            errorRequests.incrementAndGet();
            latencies.computeIfAbsent(type, k -> new ArrayList<>()).add(elapsedNanos);
        }

        void printInterval() {
            Instant now = Instant.now();
            long currentTotal = totalRequests.get();
            long intervalTotal;
            synchronized (lock) {
                intervalTotal = currentTotal - lastIntervalRequests.get();
                lastIntervalRequests.set(currentTotal);
            }
            long elapsedSec = Duration.between(lastIntervalTime, now).getSeconds();
            lastIntervalTime = now;
            double tps = elapsedSec > 0 ? (double) intervalTotal / elapsedSec : 0;
            System.out.printf("[%s] TPS: %.1f | Total: %d | Errors: %d%n",
                    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                    tps, currentTotal, errorRequests.get());
        }

        void printSummary(long durationMinutes) {
            System.out.println();
            System.out.println("=== Load Test Summary ===");
            System.out.printf("Duration: %d minutes%n", durationMinutes);
            System.out.printf("Total requests: %d%n", totalRequests.get());
            System.out.printf("Total errors: %d (%.1f%%)%n",
                    errorRequests.get(),
                    totalRequests.get() > 0 ? 100.0 * errorRequests.get() / totalRequests.get() : 0);
            System.out.println();

            for (String type : List.of("create", "update", "read")) {
                List<Long> typeLatencies = latencies.get(type);
                if (typeLatencies == null || typeLatencies.isEmpty()) {
                    System.out.printf("[%s] No requests%n", type);
                    continue;
                }
                typeLatencies.sort(Comparator.naturalOrder());
                int count = typeLatencies.size();
                long min = typeLatencies.get(0);
                long max = typeLatencies.get(count - 1);
                double avg = typeLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
                long p50 = typeLatencies.get((int) (count * 0.50));
                long p95 = typeLatencies.get((int) (count * 0.95));
                long p99 = typeLatencies.get((int) (count * 0.99));

                System.out.printf("[%s] count=%d, min=%.1fms, avg=%.1fms, max=%.1fms, p50=%.1fms, p95=%.1fms, p99=%.1fms%n",
                        type, count,
                        min / 1_000_000.0, avg / 1_000_000.0, max / 1_000_000.0,
                        p50 / 1_000_000.0, p95 / 1_000_000.0, p99 / 1_000_000.0);
            }
        }
    }
}
