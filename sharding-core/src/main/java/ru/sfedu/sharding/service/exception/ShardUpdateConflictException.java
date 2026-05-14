package ru.sfedu.sharding.service.exception;

public class ShardUpdateConflictException extends RuntimeException {

    public ShardUpdateConflictException(String objectId) {
        super("Concurrent update conflict for object: " + objectId + ". Retry the request.");
    }
}
