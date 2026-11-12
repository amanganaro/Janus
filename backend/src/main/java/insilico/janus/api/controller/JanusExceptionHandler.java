package insilico.janus.api.controller;

import insilico.janus.api.dto.ErrorDto;
import insilico.janus.api.service.InvalidRequestException;
import insilico.janus.api.service.JobNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the exceptions of the service into plain JSON errors, so that a client never receives
 * an anonymous 500.
 */
@RestControllerAdvice
public class JanusExceptionHandler {

    /**
     * @param e the exception
     * @return {@code 400} with the reason and the offending items
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorDto> onInvalidRequest(InvalidRequestException e) {
        return ResponseEntity.badRequest().body(new ErrorDto(e.getMessage(), e.getDetails()));
    }

    /**
     * @param e the exception
     * @return {@code 400} with one entry per field that failed validation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> onValidationFailure(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new ErrorDto("Invalid request", details));
    }

    /**
     * @param e the exception
     * @return {@code 404}
     */
    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorDto> onJobNotFound(JobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDto(e.getMessage()));
    }
}
