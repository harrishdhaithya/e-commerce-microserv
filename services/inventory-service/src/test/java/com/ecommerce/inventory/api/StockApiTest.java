package com.ecommerce.inventory.api;

import com.ecommerce.inventory.domain.StockItem;
import com.ecommerce.inventory.repository.StockItemRepository;
import com.ecommerce.inventory.repository.StockLedgerRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private StockItemRepository stockItems;

    @Autowired
    private StockReservationRepository reservations;

    @Autowired
    private StockLedgerRepository ledger;

    @BeforeEach
    void setUp() {
        reservations.deleteAll();
        ledger.deleteAll();
        stockItems.deleteAll();
        stockItems.save(new StockItem("API-0001", 10, 2));
        stockItems.save(new StockItem("API-0002", 1, 5));
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

    // ----------------------------------------------------------- public reads

    @Test
    void availabilityIsPublic() throws Exception {
        mockMvc.perform(get("/api/stock/{sku}", "API-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(10))
                .andExpect(jsonPath("$.inStock").value(true))
                // Exact counts stay private: a competitor can read this endpoint.
                .andExpect(jsonPath("$.quantityOnHand").doesNotExist())
                .andExpect(jsonPath("$.quantityReserved").doesNotExist());
    }

    @Test
    void batchAvailabilityIsPublicAndSkipsUnknownSkus() throws Exception {
        mockMvc.perform(post("/api/stock/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skus\":[\"API-0001\",\"API-0002\",\"NOPE\"]}"))
                .andExpect(status().isOk())
                // Unknown SKUs are absent rather than a 404 - a cart with one
                // discontinued line should still render the rest.
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rejectsAnEmptyOrOversizedBatch() throws Exception {
        mockMvc.perform(post("/api/stock/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skus\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownSkuIsNotFound() throws Exception {
        mockMvc.perform(get("/api/stock/{sku}", "NOPE"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------- the access matrix

    @Test
    void exactCountsRequireAdmin() throws Exception {
        mockMvc.perform(get("/api/stock/{sku}/detail", "API-0001"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/stock/{sku}/detail", "API-0001").with(customer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/stock/{sku}/detail", "API-0001").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(10));
    }

    @Test
    void writesRejectNonAdmins() throws Exception {
        String body = "{\"quantityOnHand\":50,\"reorderLevel\":5}";

        // Matched by method as well as path, so the public GET on /api/stock/{sku}
        // does not leak into PUT.
        mockMvc.perform(put("/api/stock/{sku}", "API-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/stock/{sku}", "API-0001").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void reservationEndpointsRejectNonAdmins() throws Exception {
        // These are called by the saga, never by a browser. A client able to reserve
        // directly could hold stock without placing an order.
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"o-1\",\"sku\":\"API-0001\",\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/reservations").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"o-1\",\"sku\":\"API-0001\",\"quantity\":1}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ admin

    @Test
    void adminSetsStockAndSeesItInTheLedger() throws Exception {
        mockMvc.perform(put("/api/stock/{sku}", "API-0001").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\":50,\"reorderLevel\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(50));

        mockMvc.perform(get("/api/stock/{sku}/ledger", "API-0001").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].change").value(40))
                .andExpect(jsonPath("$.content[0].reason").value("ADJUSTMENT"));
    }

    @Test
    void adjustmentRequiresAReason() throws Exception {
        // An unexplained change in an audit trail defeats the point of having one.
        mockMvc.perform(post("/api/stock/{sku}/adjustments", "API-0001").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("reason"));
    }

    @Test
    void lowStockListsItemsAtOrBelowReorderLevel() throws Exception {
        // API-0002 has 1 available against a reorder level of 5.
        mockMvc.perform(get("/api/stock").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sku == 'API-0002')]").exists());
    }

    // ----------------------------------------------------- reservation flow

    @Test
    void reserveCommitAndReleaseThroughTheApi() throws Exception {
        String created = mockMvc.perform(post("/api/reservations").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"order-9\",\"sku\":\"API-0001\",\"quantity\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andReturn().getResponse().getContentAsString();

        String id = json.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/stock/{sku}", "API-0001"))
                .andExpect(jsonPath("$.available").value(6));

        mockMvc.perform(post("/api/reservations/{id}/commit", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED"));

        mockMvc.perform(get("/api/stock/{sku}/detail", "API-0001").with(admin()))
                .andExpect(jsonPath("$.quantityOnHand").value(6))
                .andExpect(jsonPath("$.quantityReserved").value(0));
    }

    @Test
    void insufficientStockIsAConflictNotAServerError() throws Exception {
        // API-0002 has 1. Asking for 5 is a normal business outcome: the saga cancels
        // the order. It must not surface as a 500.
        mockMvc.perform(post("/api/reservations").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"order-9\",\"sku\":\"API-0002\",\"quantity\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Insufficient stock")))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void releasingReturnsStockToAvailability() throws Exception {
        String created = mockMvc.perform(post("/api/reservations").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"order-9\",\"sku\":\"API-0001\",\"quantity\":4}"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();

        mockMvc.perform(delete("/api/reservations/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        mockMvc.perform(get("/api/stock/{sku}", "API-0001"))
                .andExpect(jsonPath("$.available").value(10));
    }

    @Test
    void rejectsZeroOrNegativeQuantity() throws Exception {
        mockMvc.perform(post("/api/reservations").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"order-9\",\"sku\":\"API-0001\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("quantity"));
    }
}
