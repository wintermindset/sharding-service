package ru.sfedu.sharding.service.dto;

public record ShardIndexResponse(
    String objectId,
    Integer shardIndex
) {

    public static ShardIndexResponse of(String objectId, Integer shardIndex) {
        return new ShardIndexResponse(objectId, shardIndex);
    }
}
