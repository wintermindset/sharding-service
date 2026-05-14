package ru.sfedu.sharding.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShardIndexRequest(
    @NotBlank @Size(max = 64) String objectId,
    @NotNull @Min(0) @Max(1023) Integer shardIndex
) {}
