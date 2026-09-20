package com.ecommerce.catalog.api;

import com.ecommerce.catalog.domain.Category;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProductRepository products;

    @BeforeEach
    void setUp() {
        // Products first: they hold the foreign key to categories.
        products.deleteAll();
        categories.deleteAll();

        categories.save(new Category("Monitors", "monitors"));
        categories.save(new Category("Headphones", "headphones"));
    }

    @Test
    void listsCategoriesOrderedByName() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Inserted Monitors first, so this also pins the ordering.
                .andExpect(jsonPath("$[0].name").value("Headphones"))
                .andExpect(jsonPath("$[1].name").value("Monitors"));
    }

    @Test
    void exposesPublicIdAndSlugButNotInternalId() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].slug").value("headphones"))
                .andExpect(jsonPath("$[0].version").doesNotExist());
    }
}
