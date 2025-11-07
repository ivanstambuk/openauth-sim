package io.openauth.sim.rest.eudi.openid4vp;

import io.openauth.sim.application.eudi.openid4vp.Oid4vpValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
final class Oid4vpProblemDetailsAdvice {

    @ExceptionHandler(Oid4vpValidationException.class)
    ResponseEntity<Oid4vpProblemDetailsResponse> handleOid4vpValidation(Oid4vpValidationException exception) {
        var problem = exception.problemDetails();
        Oid4vpProblemDetailsResponse payload = Oid4vpProblemDetailsResponse.from(problem);
        HttpStatus status = HttpStatus.valueOf(problem.status());
        return ResponseEntity.status(status).body(payload);
    }
}
