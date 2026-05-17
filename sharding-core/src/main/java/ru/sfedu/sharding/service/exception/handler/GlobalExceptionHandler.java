package ru.sfedu.sharding.service.exception.handler;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;
import ru.sfedu.sharding.service.exception.ShardIndexAlreadyExistsException;
import ru.sfedu.sharding.service.exception.ShardIndexNotFoundException;
import ru.sfedu.sharding.service.exception.ShardUpdateConflictException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ShardIndexNotFoundException.class)
    public ProblemDetail handleNotFound(ShardIndexNotFoundException ex) {
        log.warn("Shard not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Shard Index Not Found");
        problem.setType(URI.create("urn:problem:shard-index-not-found"));
        return problem;
    }

    @ExceptionHandler(ShardIndexAlreadyExistsException.class)
    public ProblemDetail handleConflict(ShardIndexAlreadyExistsException ex) {
        log.warn("Shard already exists: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Shard Index Already Exists");
        problem.setType(URI.create("urn:problem:shard-index-already-exists"));
        return problem;
    }

    @ExceptionHandler(ShardUpdateConflictException.class)
    public ProblemDetail handleUpdateConflict(ShardUpdateConflictException ex) {
        log.warn("Shard update conflict: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Shard Update Conflict");
        problem.setType(URI.create("urn:problem:shard-update-conflict"));
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Validation Error");
        problem.setType(URI.create("urn:problem:validation-error"));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnhandled(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("urn:problem:internal-server-error"));
        return problem;
    }
}
