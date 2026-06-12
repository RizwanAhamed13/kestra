package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.webserver.filter.HeadlessUiFilter;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
@Property(name = "kestra.webserver.headless", value = "true")
class HeadlessWebserverTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    ApplicationContext applicationContext;

    @Test
    void shouldNotServeUiWhenHeadless() {
        // When / Then
        assertThatThrownBy(() -> client.toBlocking().exchange(GET("/ui/")))
            .isInstanceOf(HttpClientResponseException.class)
            .extracting(e -> ((HttpClientResponseException) e).getStatus())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldNotRedirectRootToUiWhenHeadless() {
        // When / Then
        assertThatThrownBy(() -> client.toBlocking().exchange(GET("/")))
            .isInstanceOf(HttpClientResponseException.class)
            .extracting(e -> ((HttpClientResponseException) e).getStatus())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldKeepApiAvailableWhenHeadless() {
        // When
        String response = client.toBlocking().retrieve(GET("/ping"));

        // Then
        assertThat(response).isEqualTo("pong");
    }

    @Test
    void shouldNotRegisterUiBeansWhenHeadless() {
        // Then
        assertThat(applicationContext.containsBean(HeadlessUiFilter.class)).isTrue();
        assertThat(applicationContext.containsBean(RedirectController.class)).isFalse();
        assertThat(applicationContext.containsBean(StaticFilter.class)).isFalse();
    }
}
