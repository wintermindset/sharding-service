package ru.sfedu.sharding.service.exception.handler;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.sfedu.sharding.service.exception.ShardIndexAlreadyExistsException;
import ru.sfedu.sharding.service.exception.ShardIndexNotFoundException;
import ru.sfedu.sharding.service.exception.ShardUpdateConflictException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ShardIndexNotFoundException.class)
    public ProblemDetail handleNotFound(ShardIndexNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Shard Index Not Found");
        problem.setType(URI.create("urn:problem:shard-index-not-found"));
        return problem;
    }

    @ExceptionHandler(ShardIndexAlreadyExistsException.class)
    public ProblemDetail handleConflict(ShardIndexAlreadyExistsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Shard Index Already Exists");
        problem.setType(URI.create("urn:problem:shard-index-already-exists"));
        return problem;
    }

    @ExceptionHandler(ShardUpdateConflictException.class)
    public ProblemDetail handleUpdateConflict(ShardUpdateConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Shard Update Conflict");
        problem.setType(URI.create("urn:problem:shard-update-conflict"));
        return problem;
    }
}
