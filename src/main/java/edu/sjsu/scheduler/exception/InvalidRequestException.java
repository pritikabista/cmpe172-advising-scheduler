package edu.sjsu.scheduler.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends AppException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
