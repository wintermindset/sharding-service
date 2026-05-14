package ru.sfedu.sharding.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateShardIndexRequest(
    @NotNull @Min(0) @Max(1023) Integer shardIndex
) {}
