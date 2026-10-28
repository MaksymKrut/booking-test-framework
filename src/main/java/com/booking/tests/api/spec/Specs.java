package com.booking.tests.api.spec;

import com.booking.tests.config.Config;
import com.booking.tests.util.Json;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.common.mapper.factory.Jackson2ObjectMapperFactory;
import io.restassured.config.LogConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.lessThan;

/**
 * Request/response specifications shared by all API clients. The REST Assured config is attached
 * to each request spec rather than to the global {@code RestAssured.config}.
 */
public final class Specs {

    private static final RestAssuredConfig CONFIG = RestAssuredConfig.config()
            .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                    .defaultObjectMapperType(ObjectMapperType.JACKSON_2)
                    .jackson2ObjectMapperFactory((Jackson2ObjectMapperFactory) (type, charset) -> Json.mapper()))
            .logConfig(LogConfig.logConfig().blacklistHeader("Authorization"));

    private Specs() {
    }

    /** Base URI, JSON in/out, Allure + SLF4J filters; bearer token from env {@code API_TOKEN} when set. */
    public static RequestSpecification request() {
        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setConfig(CONFIG)
                .setBaseUri(Config.get().apiBaseUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(new AllureRestAssured())
                .addFilter(new Slf4jLogFilter());
        String token = System.getenv("API_TOKEN");
        if (token != null && !token.isBlank()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    public static ResponseSpecification ok() {
        return status(200);
    }

    public static ResponseSpecification created() {
        return status(201);
    }

    /** Status code, JSON content type and the {@code api.max.response.ms} ceiling. */
    public static ResponseSpecification status(int statusCode) {
        return new ResponseSpecBuilder()
                .expectStatusCode(statusCode)
                .expectContentType(ContentType.JSON)
                .expectResponseTime(lessThan(Config.get().maxResponseMs()), TimeUnit.MILLISECONDS)
                .build();
    }
}
