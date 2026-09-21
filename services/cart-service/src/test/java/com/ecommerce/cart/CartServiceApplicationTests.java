package com.ecommerce.cart;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test. Starting the context runs the migration and then has Hibernate validate
 * every entity mapping against the resulting schema, so a column mismatch fails here
 * rather than on the first request.
 */
@SpringBootTest
@ActiveProfiles("test")
class CartServiceApplicationTests {

    @Test
    void contextLoadsAndSchemaMatchesEntities() {
    }
}
