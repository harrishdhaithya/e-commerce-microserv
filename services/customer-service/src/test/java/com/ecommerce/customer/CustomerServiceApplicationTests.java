package com.ecommerce.customer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test. Worth more than it looks: starting the context runs every Flyway
 * migration and then has Hibernate validate the entity mappings against the
 * resulting schema. A column name, length or type that disagrees fails right here
 * rather than on the first real request.
 */
@SpringBootTest
@ActiveProfiles("test")
class CustomerServiceApplicationTests {

    @Test
    void contextLoadsAndSchemaMatchesEntities() {
    }
}
