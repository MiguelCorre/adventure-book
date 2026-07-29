package com.adventurebook.api;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.adventurebook.api.dto.ValidationIssueView;
import com.adventurebook.book.BookNotFoundException;
import com.adventurebook.book.BookRejectedException;
import com.adventurebook.book.BookSlugConflictException;
import com.adventurebook.game.GameExceptions.BookNotPlayableException;
import com.adventurebook.game.GameExceptions.GameFinishedException;
import com.adventurebook.game.GameExceptions.GameNotFoundException;
import com.adventurebook.game.GameExceptions.InvalidChoiceException;
import com.adventurebook.game.GameExceptions.SectionNotFoundException;
import com.adventurebook.save.NoSavedProgressException;

/**
 * Turns domain failures into RFC 7807 responses.
 *
 * <p>Each status is chosen to tell the client something it can act on: 404 the thing is
 * not here, 409 you are too late, 422 the request was understood but the content will not
 * allow it.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://adventure-book/problems/";

    @ExceptionHandler({ BookNotFoundException.class, GameNotFoundException.class,
            NoSavedProgressException.class })
    public ProblemDetail handleNotFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage(), "not-found");
    }

    /** The section a session or save points at is gone, usually because the book changed. */
    @ExceptionHandler(SectionNotFoundException.class)
    public ProblemDetail handleMissingSection(SectionNotFoundException e) {
        return problem(HttpStatus.CONFLICT, "Section no longer exists", e.getMessage(), "section-missing");
    }

    @ExceptionHandler(GameFinishedException.class)
    public ProblemDetail handleFinished(GameFinishedException e) {
        return problem(HttpStatus.CONFLICT, "Adventure already over", e.getMessage(), "game-finished");
    }

    @ExceptionHandler(BookNotPlayableException.class)
    public ProblemDetail handleUnplayable(BookNotPlayableException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Book cannot be played", e.getMessage(), "book-invalid");
    }

    @ExceptionHandler(InvalidChoiceException.class)
    public ProblemDetail handleInvalidChoice(InvalidChoiceException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Choice not available", e.getMessage(), "invalid-choice");
    }

    /** Attaches the whole validation report so the curator can fix everything in one pass. */
    @ExceptionHandler(BookRejectedException.class)
    public ProblemDetail handleRejectedUpload(BookRejectedException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Book rejected", e.getMessage(),
                "book-rejected");
        problem.setProperty("issues", e.report().issues().stream().map(ValidationIssueView::from).toList());
        return problem;
    }

    @ExceptionHandler(BookSlugConflictException.class)
    public ProblemDetail handleSlugConflict(BookSlugConflictException e) {
        return problem(HttpStatus.CONFLICT, "Book already exists", e.getMessage(), "book-exists");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled failure serving request", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "Something went wrong handling this request.", "internal");
    }

    static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_PREFIX + type));
        return problem;
    }
}
