package com.ecommerce.catalog.api;

import com.ecommerce.catalog.domain.Category;
import com.ecommerce.catalog.domain.Product;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    private Product laptop;

    @BeforeEach
    void setUp() {
        products.deleteAll();
        categories.deleteAll();

        Category laptops = categories.save(new Category("Laptops", "laptops"));
        Category audio = categories.save(new Category("Headphones", "headphones"));

        laptop = products.save(new Product("TEST-LAP-1", "Test Ultrabook",
                "A laptop for tests", new BigDecimal("1499.0000"), laptops, null));
        products.save(new Product("TEST-HPH-1", "Test Headphones",
                "Audio for tests", new BigDecimal("349.0000"), audio, null));

        Product retired = new Product("TEST-OLD-1", "Retired Laptop",
                "Should never appear", new BigDecimal("99.0000"), laptops, null);
        retired.setActive(false);
        products.save(retired);
    }

    @Test
    void listsOnlyActiveProducts() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].sku").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("TEST-OLD-1"))));
    }

    @Test
    void filtersByCategorySlug() throws Exception {
        mockMvc.perform(get("/api/products").param("category", "headphones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("TEST-HPH-1"));
    }

    @Test
    void searchesByNameCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/products").param("q", "ULTRA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Test Ultrabook"));
    }

    @Test
    void treatsBlankQueryAsNoFilter() throws Exception {
        mockMvc.perform(get("/api/products").param("q", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void returnsProductByPublicId() throws Exception {
        mockMvc.perform(get("/api/products/{id}", laptop.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("TEST-LAP-1"))
                .andExpect(jsonPath("$.categorySlug").value("laptops"))
                // Internal id and version must not leak into the API.
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void returnsSharedErrorShapeForUnknownProduct() throws Exception {
        mockMvc.perform(get("/api/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsUnsupportedSortField() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "id,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsOversizedPageRequest() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "5000"))
                .andExpect(status().isBadRequest());
    }

  }
