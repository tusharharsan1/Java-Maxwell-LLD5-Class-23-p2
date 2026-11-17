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

    // Our service is fine. The bank behind it said no, or did not answer.
    @ExceptionHandler(GatewayException.class)
    public ProblemDetail handleBank(GatewayException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
        problem.setTitle("The bank could not take the payout");
        return problem;
    }

    // A move that the status does not allow, for example PAID to SENT.
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalMove(IllegalStateException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Not allowed for this payout");
        return problem;
    }
}
