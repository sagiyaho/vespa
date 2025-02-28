// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.BindingNotFoundException;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.service.BindingSetNotFoundException;
import org.eclipse.jetty.http.MimeTypes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.server.jetty.CompletionHandlerUtils.NOOP_COMPLETION_HANDLER;

/**
 * @author Tony Vaagenes
 * @author bjorncs
 */
class ServletResponseController {

    private static final Logger log = Logger.getLogger(ServletResponseController.class.getName());

    /**
     * The servlet spec does not require (Http)ServletResponse nor ServletOutputStream to be thread-safe. Therefore,
     * we must provide our own synchronization, since we may attempt to access these objects simultaneously from
     * different threads. (The typical cause of this is when one thread is writing a response while another thread
     * throws an exception, causing the request to fail with an error response).
     */
    private final Object monitor = new Object();

    //servletResponse must not be modified after the response has been committed.
    private final HttpServletRequest servletRequest;
    private final HttpServletResponse servletResponse;
    private final boolean developerMode;
    private final ErrorResponseContentCreator errorResponseContentCreator = new ErrorResponseContentCreator();

    //all calls to the servletOutputStreamWriter must hold the monitor first to ensure visibility of servletResponse changes.
    private final ServletOutputStreamWriter out;

    // GuardedBy("monitor")
    private boolean responseCommitted = false;

    ServletResponseController(
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse,
            Janitor janitor,
            RequestMetricReporter metricReporter,
            boolean developerMode) throws IOException {

        this.servletRequest = servletRequest;
        this.servletResponse = servletResponse;
        this.developerMode = developerMode;
        this.out = new ServletOutputStreamWriter(servletResponse.getOutputStream(), janitor, metricReporter);
    }

    /** Try to send an error response (assuming failure is recoverable) */
    void trySendErrorResponse(Throwable t) {
        synchronized (monitor) {
            try {
                sendErrorResponseIfUncommitted(t);
            } catch (Throwable suppressed) {
                t.addSuppressed(suppressed);
            } finally {
                out.close();
            }
        }
    }

    /** Close response writer and fail out any queued response content */
    void forceClose(Throwable t) { out.fail(t); }

    /**
     * When this future completes there will be no more calls against the servlet output stream or servlet response.
     * The framework is still allowed to invoke us though.
     *
     * The future might complete in the servlet framework thread, user thread or executor thread.
     */
    CompletableFuture<Void> finishedFuture() { return out.finishedFuture(); }

    ResponseHandler responseHandler() { return responseHandler; }

    private void sendErrorResponseIfUncommitted(Throwable t) {
        if (!responseCommitted) {
            responseCommitted = true;
            servletResponse.setHeader(HttpHeaders.Names.EXPIRES, null);
            servletResponse.setHeader(HttpHeaders.Names.LAST_MODIFIED, null);
            servletResponse.setHeader(HttpHeaders.Names.CACHE_CONTROL, null);
            servletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, null);
            servletResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, null);
            String reasonPhrase = getReasonPhrase(t, developerMode);
            int statusCode = getStatusCode(t);
            setStatus(servletResponse, statusCode, reasonPhrase);
            // If we are allowed to have a body
            if (statusCode != HttpServletResponse.SC_NO_CONTENT &&
                    statusCode != HttpServletResponse.SC_NOT_MODIFIED &&
                    statusCode != HttpServletResponse.SC_PARTIAL_CONTENT &&
                    statusCode >= HttpServletResponse.SC_OK) {
                servletResponse.setHeader(HttpHeaders.Names.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
                servletResponse.setContentType(MimeTypes.Type.TEXT_HTML_8859_1.toString());
                byte[] errorContent = errorResponseContentCreator
                        .createErrorContent(servletRequest.getRequestURI(), statusCode, reasonPhrase);
                servletResponse.setContentLength(errorContent.length);
                out.writeBuffer(ByteBuffer.wrap(errorContent), NOOP_COMPLETION_HANDLER);
            } else {
                servletResponse.setContentLength(0);
            }
        } else {
            RuntimeException exceptionWithStackTrace = new RuntimeException(t);
            log.log(Level.FINE, "Response already committed, can't change response code", exceptionWithStackTrace);
        }
    }

    private static int getStatusCode(Throwable t) {
        if (t instanceof BindingNotFoundException) {
            return HttpServletResponse.SC_NOT_FOUND;
        } else if (t instanceof BindingSetNotFoundException) {
            return HttpServletResponse.SC_NOT_FOUND;
        } else if (t instanceof RequestException) {
            return ((RequestException)t).getResponseStatus();
        } else if (t instanceof TimeoutException) {
            // E.g stream idle timeout for HTTP/2
            return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        } else {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
    }

    private static String getReasonPhrase(Throwable t, boolean developerMode) {
        if (developerMode) {
            final StringWriter out = new StringWriter();
            t.printStackTrace(new PrintWriter(out));
            return out.toString();
        } else if (t.getMessage() != null) {
            return t.getMessage();
        } else {
            return t.toString();
        }
    }

    private void setResponse(Response jdiscResponse) {
        synchronized (monitor) {
            servletRequest.setAttribute(HttpResponseStatisticsCollector.requestTypeAttribute, jdiscResponse.getRequestType());
            if (responseCommitted) {
                log.log(Level.FINE,
                        jdiscResponse.getError(),
                        () -> "Response already committed, can't change response code. " +
                                "From: " + servletResponse.getStatus() + ", To: " + jdiscResponse.getStatus());

                //TODO: should throw an exception here, but this breaks unit tests.
                //The failures will now instead happen when writing buffers.
                out.close();
                return;
            }

            if (jdiscResponse instanceof HttpResponse) {
                setStatus(servletResponse, jdiscResponse.getStatus(), ((HttpResponse) jdiscResponse).getMessage());
            } else {
                String message = Optional.ofNullable(jdiscResponse.getError())
                        .flatMap(error -> Optional.ofNullable(error.getMessage()))
                        .orElse(null);
                setStatus(servletResponse, jdiscResponse.getStatus(), message);
            }
            for (final Map.Entry<String, String> entry : jdiscResponse.headers().entries()) {
                servletResponse.addHeader(entry.getKey(), entry.getValue());
            }
            if (servletResponse.getContentType() == null) {
                servletResponse.setContentType("text/plain;charset=utf-8");
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void setStatus(HttpServletResponse response, int statusCode, String reasonPhrase) {
        if (reasonPhrase != null) {
            // Sets the status line: a status code along with a custom message.
            // Using a custom status message is deprecated in the Servlet API. No alternative exist.
            response.setStatus(statusCode, reasonPhrase); // DEPRECATED
        } else {
            response.setStatus(statusCode);
        }
    }


    private void ensureCommitted() {
        synchronized (monitor) {
            responseCommitted = true;
        }
    }

    private final ResponseHandler responseHandler = new ResponseHandler() {
        @Override
        public ContentChannel handleResponse(Response response) {
            setResponse(response);
            return responseContentChannel;
        }
    };

    private final ContentChannel responseContentChannel = new ContentChannel() {
        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            ensureCommitted();
            out.writeBuffer(buf, handlerOrNoopHandler(handler));
        }

        @Override
        public void close(CompletionHandler handler) {
            ensureCommitted();
            out.close(handlerOrNoopHandler(handler));
        }

        private CompletionHandler handlerOrNoopHandler(CompletionHandler handler) {
            return handler != null ? handler : NOOP_COMPLETION_HANDLER;
        }
    };
}
