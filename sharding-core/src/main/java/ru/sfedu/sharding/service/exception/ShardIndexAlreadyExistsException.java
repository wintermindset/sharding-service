package ru.sfedu.sharding.service.exception;

public class ShardIndexAlreadyExistsException extends RuntimeException {

    public ShardIndexAlreadyExistsException(String objectId) {
        super("Shard index already exists for object: " + objectId);
    }
}
