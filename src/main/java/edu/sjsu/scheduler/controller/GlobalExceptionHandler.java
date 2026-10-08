package edu.sjsu.scheduler.controller;

import edu.sjsu.scheduler.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

/**
 * One place that turns exceptions into an error page with a standard HTTP status.
 *   SlotAlreadyBookedException / ConflictException -> 409
 *   ForbiddenException / AccessDeniedException     -> 403
 *   InvalidRequestException / bad parameter types  -> 400
 *   NotFoundException / unknown URL                -> 404
 *   anything else                                  -> 500
 * The page only shows a short message, never a stack trace (that goes to the server log).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ModelAndView handleApp(AppException ex, HttpServletRequest request) {
        return errorPage(ex.getStatus(), ex.getMessage(), request);
    }

    /** e.g. /slots?page=abc or /slots/xyz/book */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return errorPage(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return errorPage(HttpStatus.FORBIDDEN, "You don't have permission to do that.", request);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleOther(Exception ex, HttpServletRequest request) {
        // Spring's own MVC exceptions (missing parameter, 404 no resource, 405 ...) carry their status.
        if (ex instanceof ErrorResponse er) {
            HttpStatusCode code = er.getStatusCode();
            HttpStatus status = HttpStatus.resolve(code.value());
            return errorPage(status == null ? HttpStatus.BAD_REQUEST : status, null, request);
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return errorPage(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.", request);
    }

    private ModelAndView errorPage(HttpStatus status, String message, HttpServletRequest request) {
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("error", status.getReasonPhrase());
        mav.addObject("message", message);
        mav.addObject("username", request.getUserPrincipal() == null ? null : request.getUserPrincipal().getName());
        mav.addObject("isStudent", request.isUserInRole("CUSTOMER"));
        mav.addObject("isAdvisor", request.isUserInRole("PROVIDER"));
        return mav;
    }
}
