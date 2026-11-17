package com.shopnest.payout;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Already finished, and it is your example: a payout that does not exist is a 404. */
    @ExceptionHandler(NoSuchPayoutException.class)
    public ProblemDetail handleNotFound(NoSuchPayoutException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("No such payout");
        return problem;
    }

    // TODO 4: add two more handlers, in the same style as the one above. Each one answers with a ProblemDetail:
    //   - GatewayException (the bank said no, or did not answer): HTTP 502 (BAD_GATEWAY), the title
    //     "The bank could not take the payout", and the message of the exception as the detail.
    //   - IllegalStateException (a move that the status does not allow): HTTP 409 (CONFLICT), the title
    //     "Not allowed for this payout", and the message of the exception as the detail.
}
