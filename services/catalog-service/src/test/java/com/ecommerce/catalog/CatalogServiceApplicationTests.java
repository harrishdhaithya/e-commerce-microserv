package com.ecommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test. Worth more than it looks: starting the context runs every Flyway
 * migration and then has Hibernate validate the entity mappings against the
 * resulting schema. A typo in a column name fails right here.
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogServiceApplicationTests {

    @Test
    void contextLoadsAndSchemaMatchesEntities() {
    }
}
