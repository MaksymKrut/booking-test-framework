package com.booking.tests.api.spec;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Logs every HTTP call on logger {@code http}: method, URL, status and time at INFO, bodies at DEBUG.
 * Headers are never logged.
 */
public final class Slf4jLogFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger("http");

    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification responseSpec,
                           FilterContext ctx) {
        String method = request.getMethod();
        String uri = request.getURI();
        log.info("--> {} {}", method, uri);
        Object body = request.getBody();
        if (body != null) {
            log.debug("--> body: {}", body instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : body);
        }

        Response response = ctx.next(request, responseSpec);

        log.info("<-- {} {} {} ({} ms)", response.statusCode(), method, uri, response.getTimeIn(TimeUnit.MILLISECONDS));
        if (log.isDebugEnabled()) {
            String responseBody = response.asString();
            if (!responseBody.isEmpty()) {
                log.debug("<-- body: {}", responseBody);
            }
        }
        return response;
    }
}
