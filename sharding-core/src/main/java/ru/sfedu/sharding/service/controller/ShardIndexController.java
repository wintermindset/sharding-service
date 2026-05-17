package ru.sfedu.sharding.service.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ru.sfedu.sharding.service.dto.ShardIndexRequest;
import ru.sfedu.sharding.service.dto.ShardIndexResponse;
import ru.sfedu.sharding.service.dto.UpdateShardIndexRequest;
import ru.sfedu.sharding.service.service.ShardingService;

@RestController
@RequestMapping("/api/v1/shard-indices")
@RequiredArgsConstructor
public class ShardIndexController {

    private final ShardingService shardingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShardIndexResponse create(@Valid @RequestBody ShardIndexRequest request) {
        return shardingService.createShardIndex(request.objectId(), request.shardIndex());
    }

    @PutMapping("/{objectId}")
    public ShardIndexResponse update(
            @PathVariable String objectId,
            @Valid @RequestBody UpdateShardIndexRequest request
    ) {
        return shardingService.updateShardIndex(objectId, request.shardIndex());
    }

    @GetMapping("/{objectId}")
    public ShardIndexResponse get(@PathVariable String objectId) {
        return shardingService.getShardIndex(objectId);
    }
}
