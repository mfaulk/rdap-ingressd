package net.apnic.rdap.error.controller;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

import net.apnic.rdap.rdap.http.RDAPConstants;
import net.apnic.rdap.rdap.RDAPError;
import net.apnic.rdap.rdap.RDAPObjectFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Error controller for spring errors.
 *
 * This is used in conjunction with the error controller. Unfortunately to the way
 * errors are handled in Zuul any exception thrown is redirected to the error
 * path.
 *
 * Its the job of this class to take the exception and work out the most
 * appropriate RDAP response.
 */
@ControllerAdvice
public class ErrorController
{
    private RDAPObjectFactory rdapObjectFactory = null;
    private HttpHeaders responseHeaders = null;

    /**
     * Main constructor which takes the services RDAPObjectFactory to construct
     * RDAPError's from.
     *
     * @param rdapObjectFactory Object factory to construct RDAPError objects
     *                          from
     */
    @Autowired
    public ErrorController(RDAPObjectFactory rdapObjectFactory)
    {
        this.rdapObjectFactory = rdapObjectFactory;
        setupResponseHeaders();
    }

    /**
     * Constructs a new RDAPError object for the provided HttpStatus and
     * context.
     *
     * @param status HttpStatus for the RDAPError
     * @param context The context the error request was made in. This will be
     *                the request URL in most cases
     */
    private ResponseEntity<RDAPError> createErrorResponse(HttpStatus status,
                                                         String context)
    {
        return createErrorResponse(status, null, context);
    }

    /**
     * Constructs a new RDAPError object for the provided HttpStatus
     * description, and context.
     *
     * @param status HttpStatus for the RDAPError
     * @param description Description for the error object
     * @param context The context the error request was made in. This will be
     *                the request URL in most cases
     */
    private ResponseEntity<RDAPError> createErrorResponse(HttpStatus status,
                                                         String description,
                                                         String context)
    {
        List<String> descriptions = Optional.ofNullable(description).map(Collections::singletonList).orElse(Collections.emptyList());
        String errorCode = "" + status.value();
        String title = status.getReasonPhrase();
        RDAPError error = rdapObjectFactory.createErrorObject(
            context, descriptions, errorCode, title);

        return new ResponseEntity<RDAPError>(error, responseHeaders,
                                             status);
    }

    /**
     * Generic spring handler for all exceptions that occur outside of Zuul
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RDAPError> handleException(HttpServletRequest request)
    {
        String context = request.getRequestURL().toString();
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, context);
    }

    /**
     * Handles all Spring exceptions outside of Zuul where no rest handler can
     * be found. These are always 404 errors.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<RDAPError> notFound(HttpServletRequest request)
    {
        String context = request.getRequestURL().toString();
        return createErrorResponse(HttpStatus.NOT_FOUND, context);
    }

    private void setupResponseHeaders()
    {
        responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(RDAPConstants.RDAP_MEDIA_TYPE);
    }
}
