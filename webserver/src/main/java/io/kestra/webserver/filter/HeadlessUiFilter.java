package io.kestra.webserver.filter;

import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;

/**
 * Filter that disables the UI when the webserver runs in headless mode
 * ({@code kestra.webserver.headless: true}): every {@code /ui/**} request is
 * answered with a 404 before reaching the static-resources route.
 */
@Filter("/ui/**")
@Requires(property = "kestra.webserver.headless", value = "true")
public class HeadlessUiFilter implements HttpServerFilter {

    @Override
    public int getOrder() {
        return ServerFilterPhase.FIRST.order();
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Publishers.just(HttpResponse.notFound());
    }
}
