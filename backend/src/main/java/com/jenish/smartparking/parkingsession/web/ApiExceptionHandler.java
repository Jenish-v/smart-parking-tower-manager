package com.jenish.smartparking.parkingsession.web;

import com.jenish.smartparking.allocation.domain.ParkingCapacityExceededException;
import com.jenish.smartparking.facility.domain.FacilityNotFoundException;
import com.jenish.smartparking.parkingsession.application.ActiveParkingSessionExistsException;
import com.jenish.smartparking.parkingsession.application.IdempotencyConflictException;
import com.jenish.smartparking.parkingsession.application.NoActiveParkingSessionException;
import com.jenish.smartparking.pricing.application.NoApplicableRatePlanException;
import com.jenish.smartparking.reservation.application.OverlappingVehicleReservationException;
import com.jenish.smartparking.reservation.application.ReservationArrivalSizeMismatchException;
import com.jenish.smartparking.reservation.application.ReservationCapacityExceededException;
import com.jenish.smartparking.reservation.application.ReservationIdentifierConflictException;
import com.jenish.smartparking.reservation.application.ReservationNotFoundException;
import com.jenish.smartparking.reservation.domain.InvalidReservationStateException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public final class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<Violation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new Violation(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                "One or more request fields are invalid.",
                request);
        problem.setProperty("violations", violations);
        return response(problem);
    }

    @ExceptionHandler({
        HandlerMethodValidationException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingRequestHeaderException.class
    })
    public ResponseEntity<ProblemDetail> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Invalid request",
                "The request could not be parsed or validated.",
                request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleDomainValidation(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Invalid request",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(ActiveParkingSessionExistsException.class)
    public ResponseEntity<ProblemDetail> handleActiveSession(
            ActiveParkingSessionExistsException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                "ACTIVE_SESSION_EXISTS",
                "Active parking session exists",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(
            IdempotencyConflictException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_CONFLICT",
                "Idempotency key conflict",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(ParkingCapacityExceededException.class)
    public ResponseEntity<ProblemDetail> handleCapacity(
            ParkingCapacityExceededException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                "NO_COMPATIBLE_SPACE",
                "No compatible parking space",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(ReservationCapacityExceededException.class)
    public ResponseEntity<ProblemDetail> handleReservationCapacity(
            ReservationCapacityExceededException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                "RESERVATION_CAPACITY_EXCEEDED",
                "Reservation capacity exceeded",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(ReservationArrivalSizeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleReservationArrivalSizeMismatch(
            ReservationArrivalSizeMismatchException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                "RESERVATION_SIZE_MISMATCH",
                "Reservation size mismatch",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(OverlappingVehicleReservationException.class)
    public ResponseEntity<ProblemDetail> handleOverlappingReservation(
            OverlappingVehicleReservationException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                "OVERLAPPING_VEHICLE_RESERVATION",
                "Overlapping vehicle reservation",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(ReservationIdentifierConflictException.class)
    public ResponseEntity<ProblemDetail> handleReservationIdentifierConflict(
            ReservationIdentifierConflictException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                "RESERVATION_IDENTIFIER_CONFLICT",
                "Reservation identifier conflict",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(InvalidReservationStateException.class)
    public ResponseEntity<ProblemDetail> handleReservationState(
            InvalidReservationStateException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                "INVALID_RESERVATION_STATE",
                "Invalid reservation state",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(FacilityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleMissingFacility(
            FacilityNotFoundException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "FACILITY_NOT_FOUND",
                "Facility not found",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(NoActiveParkingSessionException.class)
    public ResponseEntity<ProblemDetail> handleMissingSession(
            NoActiveParkingSessionException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "ACTIVE_SESSION_NOT_FOUND",
                "Active parking session not found",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(NoApplicableRatePlanException.class)
    public ResponseEntity<ProblemDetail> handleMissingRatePlan(
            NoApplicableRatePlanException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "RATE_PLAN_UNAVAILABLE",
                "Rate plan unavailable",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleMissingReservation(
            ReservationNotFoundException exception,
            HttpServletRequest request) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "RESERVATION_NOT_FOUND",
                "Reservation not found",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(TransientDataAccessException.class)
    public ResponseEntity<ProblemDetail> handleTransientDatabaseFailure(
            TransientDataAccessException exception,
            HttpServletRequest request) {
        LOGGER.warn("Transient database failure while handling {}", request.getRequestURI(), exception);
        return response(problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DATABASE_UNAVAILABLE",
                "Database temporarily unavailable",
                "The operation can be retried with the same idempotency key.",
                request));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDatabaseFailure(
            DataAccessException exception,
            HttpServletRequest request) {
        LOGGER.error("Database failure while handling {}", request.getRequestURI(), exception);
        return response(problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "DATABASE_ERROR",
                "Database operation failed",
                "The operation could not be completed.",
                request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedFailure(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error("Unexpected failure while handling {}", request.getRequestURI(), exception);
        return response(problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Internal server error",
                "The operation could not be completed.",
                request));
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:smart-parking:problem:" + code.toLowerCase().replace('_', '-')));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    public record Violation(String field, String message) {
    }
}
