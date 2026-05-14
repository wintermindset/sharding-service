package ru.sfedu.sharding.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ru.sfedu.sharding.service.dto.ShardIndexRequest;
import ru.sfedu.sharding.service.dto.ShardIndexResponse;
import ru.sfedu.sharding.service.dto.UpdateShardIndexRequest;
import ru.sfedu.sharding.service.exception.ShardIndexAlreadyExistsException;
import ru.sfedu.sharding.service.exception.ShardIndexNotFoundException;
import ru.sfedu.sharding.service.exception.handler.GlobalExceptionHandler;
import ru.sfedu.sharding.service.service.ShardingService;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShardIndexControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ShardingService shardingService;

    @BeforeEach
    void setUp() {
        ShardIndexController controller = new ShardIndexController(shardingService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void create_shouldReturn201() throws Exception {
        ShardIndexRequest request = new ShardIndexRequest("obj-0000000001", 5);
        when(shardingService.createShardIndex("obj-0000000001", 5))
                .thenReturn(new ShardIndexResponse("obj-0000000001", 5));

        mockMvc.perform(post("/api/v1/shard-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objectId").value("obj-0000000001"))
                .andExpect(jsonPath("$.shardIndex").value(5));
    }

    @Test
    void create_shouldReturn409WhenExists() throws Exception {
        ShardIndexRequest request = new ShardIndexRequest("obj-0000000001", 5);
        when(shardingService.createShardIndex(anyString(), anyInt()))
                .thenThrow(new ShardIndexAlreadyExistsException("obj-0000000001"));

        mockMvc.perform(post("/api/v1/shard-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void update_shouldReturn200() throws Exception {
        UpdateShardIndexRequest request = new UpdateShardIndexRequest(10);
        when(shardingService.updateShardIndex("obj-0000000001", 10))
                .thenReturn(new ShardIndexResponse("obj-0000000001", 10));

        mockMvc.perform(put("/api/v1/shard-indices/obj-0000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value("obj-0000000001"))
                .andExpect(jsonPath("$.shardIndex").value(10));
    }

    @Test
    void update_shouldReturn404WhenNotFound() throws Exception {
        UpdateShardIndexRequest request = new UpdateShardIndexRequest(10);
        when(shardingService.updateShardIndex("nonexistent", 10))
                .thenThrow(new ShardIndexNotFoundException("nonexistent"));

        mockMvc.perform(put("/api/v1/shard-indices/nonexistent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_shouldReturn200() throws Exception {
        when(shardingService.getShardIndex("obj-0000000001"))
                .thenReturn(new ShardIndexResponse("obj-0000000001", 5));

        mockMvc.perform(get("/api/v1/shard-indices/obj-0000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value("obj-0000000001"))
                .andExpect(jsonPath("$.shardIndex").value(5));
    }

    @Test
    void get_shouldReturn404WhenNotFound() throws Exception {
        when(shardingService.getShardIndex("nonexistent"))
                .thenThrow(new ShardIndexNotFoundException("nonexistent"));

        mockMvc.perform(get("/api/v1/shard-indices/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_shouldReturn400WhenInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/shard-indices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectId\":\"\",\"shardIndex\":null}"))
                .andExpect(status().isBadRequest());
    }
}
