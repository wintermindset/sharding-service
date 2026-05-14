package ru.sfedu.sharding.service.exception;

public class ShardIndexNotFoundException extends RuntimeException {

    public ShardIndexNotFoundException(String objectId) {
        super("Shard index not found for object: " + objectId);
    }
}
