package com.ecommerce.catalog.api;

import com.ecommerce.catalog.domain.Category;
import com.ecommerce.catalog.domain.Product;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin surface: public reads, ADMIN-only writes.
 *
 * <p>The 401/403/200 progression below is the point of the whole class. If a write
 * ever returns 200 without a role, either @EnableMethodSecurity is missing or the
 * filter chain matched too broadly - and both fail open.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogAdminTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    private Category laptops;
    private Product laptop;

    @BeforeEach
    void setUp() {
        products.deleteAll();
        categories.deleteAll();
        laptops = categories.save(new Category("Laptops", "laptops"));
        laptop = products.save(new Product("LAP-0001", "Test Ultrabook", "For tests",
                new BigDecimal("1499.0000"), laptops, null));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(b -> b.subject("sub-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(b -> b.subject("sub-customer"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private String productBody(String sku, String name, String price, String categorySlug) {
        return """
                {"sku":"%s","name":"%s","price":%s,"categorySlug":"%s"}
                """.formatted(sku, name, price, categorySlug);
    }

    // ------------------------------------------------------- the access matrix

    @Test
    void browsingStaysPublic() throws Exception {
        // The regression that matters: adding the resource-server dependency must not
        // have closed the storefront.
        mockMvc.perform(get("/api/products")).andExpect(status().isOk());
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/{id}", laptop.getPublicId())).andExpect(status().isOk());
    }

    @Test
    void writesRejectAnonymousCallers() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("NEW-1", "New", "10.00", "laptops")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writesRejectAuthenticatedNonAdmins() throws Exception {
        // 403, not 401: the token is valid, the role is missing.
        mockMvc.perform(post("/api/products")
                        .with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("NEW-1", "New", "10.00", "laptops")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products/{id}", laptop.getPublicId()).with(customer()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/categories")
                        .with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"slug\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------- products

    @Test
    void adminCreatesProduct() throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("KBD-9001", "Test Keyboard", "99.99", "laptops")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.sku").value("KBD-9001"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(2, products.count());
        // Created active, so it shows up for shoppers immediately.
        mockMvc.perform(get("/api/products").param("q", "Test Keyboard"))
                .andExpect(jsonPath("$.totalElements").value(1));
        assertFalse(json.readTree(response).get("id").asText().isBlank());
    }

    @Test
    void rejectsDuplicateSku() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("LAP-0001", "Clash", "10.00", "laptops")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("LAP-0001")));
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("NEW-2", "Orphan", "10.00", "no-such-category")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsNegativePrice() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("NEW-3", "Cheap", "-1.00", "laptops")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("price"));
    }

    @Test
    void adminUpdatesProduct() throws Exception {
        mockMvc.perform(put("/api/products/{id}", laptop.getPublicId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("LAP-0001", "Renamed Ultrabook", "1599.00", "laptops")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Ultrabook"))
                .andExpect(jsonPath("$.price").value(1599.00));
    }

    @Test
    void refusesToChangeSku() throws Exception {
        // The SKU is a business key other records point at. Changing it silently
        // would break those references, so it is immutable.
        mockMvc.perform(put("/api/products/{id}", laptop.getPublicId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("LAP-9999", "Renamed", "10.00", "laptops")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("LAP-0001")));
    }

    @Test
    void discontinueHidesFromBrowsingButKeepsTheRow() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", laptop.getPublicId()).with(admin()))
                .andExpect(status().isNoContent());

        // Gone from the storefront...
        mockMvc.perform(get("/api/products"))
                .andExpect(jsonPath("$.totalElements").value(0));
        // ...but still resolvable, so an order history page can render what was bought.
        mockMvc.perform(get("/api/products/{id}", laptop.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("LAP-0001"));
        assertEquals(1, products.count());
    }

    @Test
    void updateOfUnknownProductIsNotFound() throws Exception {
        mockMvc.perform(put("/api/products/{id}", UUID.randomUUID())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("X-1", "X", "1.00", "laptops")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ categories

    @Test
    void adminCreatesCategory() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Monitors\",\"slug\":\"monitors\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("monitors"));
    }

    @Test
    void rejectsMalformedSlug() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"slug\":\"Not A Slug\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("slug"));
    }

    @Test
    void rejectsDuplicateSlug() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Another\",\"slug\":\"laptops\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesToDeleteCategoryStillInUse() throws Exception {
        // The foreign key would reject this anyway, but as an opaque constraint
        // violation. A 409 naming the count is something an admin can act on.
        mockMvc.perform(delete("/api/categories/{id}", laptops.getPublicId()).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("1 product")));
    }

    @Test
    void deletesEmptyCategory() throws Exception {
        Category empty = categories.save(new Category("Empty", "empty"));

        mockMvc.perform(delete("/api/categories/{id}", empty.getPublicId()).with(admin()))
                .andExpect(status().isNoContent());

        assertEquals(1, categories.count());
    }

    @Test
    void renamesCategoryButRefusesSlugChange() throws Exception {
        mockMvc.perform(put("/api/categories/{id}", laptops.getPublicId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Notebooks\",\"slug\":\"laptops\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Notebooks"));

        // The slug is in every category URL; changing it would 404 existing links.
        mockMvc.perform(put("/api/categories/{id}", laptops.getPublicId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Notebooks\",\"slug\":\"notebooks\"}"))
                .andExpect(status().isConflict());
    }
}
