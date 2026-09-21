package com.ecommerce.cart.api;

import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.cart.service.CatalogClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cart endpoints, with catalog-service mocked.
 *
 * <p>Mocked rather than stubbed over HTTP because the interesting behaviour is
 * cart-side: ownership, merging, and how a changed price is reported. Whether the
 * catalog answers correctly is its own suite's job. The Phase 5 contract tests are
 * what will pin the boundary between the two.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartControllerTest {

    private static final String LAPTOP = "a1b2c3d4-0001-4e5f-8a9b-000000000001";
    private static final String KEYBOARD = "a1b2c3d4-0005-4e5f-8a9b-000000000005";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CartRepository carts;

    @MockitoBean
    private CatalogClient catalog;

    @BeforeEach
    void setUp() {
        carts.deleteAll();

        when(catalog.findProduct(LAPTOP)).thenReturn(Optional.of(new CatalogClient.CatalogProduct(
                LAPTOP, "LAP-0001", "Meridian 14 Ultrabook", new BigDecimal("1499.0000"), null)));
        when(catalog.findProduct(KEYBOARD)).thenReturn(Optional.of(new CatalogClient.CatalogProduct(
                KEYBOARD, "KBD-0001", "Tactile 87", new BigDecimal("179.0000"), null)));
    }

    private static RequestPostProcessor customer(String sub) {
        return jwt().jwt(b -> b.subject(sub))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static String addBody(String productId, int quantity) {
        return "{\"productId\":\"%s\",\"quantity\":%d}".formatted(productId, quantity);
    }

    // ------------------------------------------------------- anonymous access

    @Test
    void anonymousShopperCanUseACartWithOnlyAToken() throws Exception {
        // The whole point of this service's odd security posture: filling a basket
        // must not require an account.
        mockMvc.perform(post("/api/cart/items")
                        .header(CartController.CART_TOKEN_HEADER, "anon-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(LAPTOP, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.subtotal").value(2998.0));
    }

    @Test
    void requestWithNeitherTokenNorHeaderIsRejected() throws Exception {
        // CartOwner.of refuses to guess, so this is a 400 rather than a silently
        // shared "default" cart.
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCartsAreIsolatedByToken() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                .header(CartController.CART_TOKEN_HEADER, "anon-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addBody(LAPTOP, 1)));

        mockMvc.perform(get("/api/cart").header(CartController.CART_TOKEN_HEADER, "anon-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    // -------------------------------------------------------- signed-in carts

    @Test
    void getCreatesAnEmptyCartOnFirstCall() throws Exception {
        mockMvc.perform(get("/api/cart").with(customer("sub-alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.id").isString());

        assertEquals(1, carts.count());
    }

    @Test
    void customersCannotSeeEachOthersCarts() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                .with(customer("sub-alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addBody(LAPTOP, 3)));

        mockMvc.perform(get("/api/cart").with(customer("sub-bob")))
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void tokenWinsOverAStaleCartHeader() throws Exception {
        // A leftover X-Cart-Token in the SPA must not redirect a signed-in shopper's
        // writes into an anonymous basket.
        mockMvc.perform(post("/api/cart/items")
                .with(customer("sub-alice"))
                .header(CartController.CART_TOKEN_HEADER, "stale-anon")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addBody(LAPTOP, 1)));

        mockMvc.perform(get("/api/cart").header(CartController.CART_TOKEN_HEADER, "stale-anon"))
                .andExpect(jsonPath("$.itemCount").value(0));
        mockMvc.perform(get("/api/cart").with(customer("sub-alice")))
                .andExpect(jsonPath("$.itemCount").value(1));
    }

    // --------------------------------------------------------------- mutation

    @Test
    void addingTheSameProductMergesLines() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 1)));

        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 2)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void priceComesFromTheCatalogNotTheRequest() throws Exception {
        // A client-supplied price would let a caller set their own. The request DTO
        // has no price field at all, and this pins that.
        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + LAPTOP + "\",\"quantity\":1,\"unitPrice\":0.01}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(1499.0));
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        when(catalog.findProduct("missing")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(addBody("missing", 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchingQuantityToZeroRemovesTheLine() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 2)));

        mockMvc.perform(patch("/api/cart/items/{id}", LAPTOP).with(customer("sub-alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void rejectsQuantityAboveTheCeiling() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 9999)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("quantity"));
    }

    @Test
    void removingAndClearing() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 1)));
        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                .contentType(MediaType.APPLICATION_JSON).content(addBody(KEYBOARD, 1)));

        mockMvc.perform(delete("/api/cart/items/{id}", LAPTOP).with(customer("sub-alice")))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(delete("/api/cart").with(customer("sub-alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void removingAnAbsentLineIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/cart/items/{id}", LAPTOP).with(customer("sub-alice")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ merge

    @Test
    void mergeRequiresAuthentication() throws Exception {
        // Merging is what binds an anonymous basket to an identity, so it cannot be
        // done anonymously.
        mockMvc.perform(post("/api/cart/merge").header(CartController.CART_TOKEN_HEADER, "anon-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mergeClaimsAnAnonymousCartWhenTheCustomerHasNone() throws Exception {
        mockMvc.perform(post("/api/cart/items").header(CartController.CART_TOKEN_HEADER, "anon-1")
                .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 2)));

        mockMvc.perform(post("/api/cart/merge").with(customer("sub-alice"))
                        .header(CartController.CART_TOKEN_HEADER, "anon-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(2));

        // Claimed, not copied: still one cart row.
        assertEquals(1, carts.count());
    }

    @Test
    void mergeSumsQuantitiesWhenBothCartsExist() throws Exception {
        mockMvc.perform(post("/api/cart/items").header(CartController.CART_TOKEN_HEADER, "anon-1")
                .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 2)));
        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 1)));

        mockMvc.perform(post("/api/cart/merge").with(customer("sub-alice"))
                        .header(CartController.CART_TOKEN_HEADER, "anon-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        // And the anonymous cart is emptied rather than left as a duplicate basket.
        mockMvc.perform(get("/api/cart").header(CartController.CART_TOKEN_HEADER, "anon-1"))
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    // ------------------------------------------------------------- validation

    @Test
    void validateReportsAPriceChangeAndRefreshesTheSnapshot() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 1)));

        // The catalog has moved since the item was added.
        when(catalog.findProduct(LAPTOP)).thenReturn(Optional.of(new CatalogClient.CatalogProduct(
                LAPTOP, "LAP-0001", "Meridian 14 Ultrabook", new BigDecimal("1599.0000"), null)));

        mockMvc.perform(post("/api/cart/validate").with(customer("sub-alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[0].type").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.issues[0].oldPrice").value(1499.0))
                .andExpect(jsonPath("$.issues[0].newPrice").value(1599.0))
                // The cart now reflects the new price, so what is confirmed is what
                // gets charged.
                .andExpect(jsonPath("$.cart.items[0].unitPrice").value(1599.0));
    }

    @Test
    void validateFlagsADiscontinuedProduct() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 1)));

        when(catalog.findProduct(LAPTOP)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/cart/validate").with(customer("sub-alice")))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[0].type").value("UNAVAILABLE"));
    }

    @Test
    void validatePassesWhenNothingChanged() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 1)));

        mockMvc.perform(post("/api/cart/validate").with(customer("sub-alice")))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.issues.length()").value(0));
    }

    @Test
    void catalogOutageIsNotReportedAsAMissingProduct() throws Exception {
        when(catalog.findProduct(anyString()))
                .thenThrow(new CatalogClient.CatalogUnavailableException("catalog down", null));

        // 500, not 404. "The catalog is down" and "that product does not exist" are
        // different problems and must not look identical to the client.
        mockMvc.perform(post("/api/cart/items").with(customer("sub-alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(addBody(LAPTOP, 1)))
                .andExpect(status().isInternalServerError());
    }
}
