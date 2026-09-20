package com.ecommerce.customer.api;

import com.ecommerce.customer.repository.AddressRepository;
import com.ecommerce.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private AddressRepository addresses;

    @BeforeEach
    void setUp() {
        addresses.deleteAll();
        customers.deleteAll();
    }

    /** Keycloak's token shape: identity in `sub`, roles nested in realm_access. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor customer(String sub) {
        return jwt()
                .jwt(builder -> builder
                        .subject(sub)
                        .claim("email", sub + "@test.local")
                        .claim("given_name", "Test")
                        .claim("family_name", "Customer"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String sub) {
        return jwt()
                .jwt(builder -> builder.subject(sub).claim("email", sub + "@test.local"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/customers/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void provisionsCustomerOnFirstCall() throws Exception {
        mockMvc.perform(get("/api/customers/me").with(customer("sub-alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sub-alice@test.local"))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.loyaltyTier").value("STANDARD"))
                // Keycloak's subject must not leak into the API.
                .andExpect(jsonPath("$.keycloakId").doesNotExist());

        org.junit.jupiter.api.Assertions.assertEquals(1, customers.count());
    }

    @Test
    void reusesTheSameCustomerOnSecondCall() throws Exception {
        String first = mockMvc.perform(get("/api/customers/me").with(customer("sub-bob")))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get("/api/customers/me").with(customer("sub-bob")))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(first, second);
        org.junit.jupiter.api.Assertions.assertEquals(1, customers.count());
    }

    @Test
    void separateSubjectsGetSeparateCustomers() throws Exception {
        mockMvc.perform(get("/api/customers/me").with(customer("sub-one"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/customers/me").with(customer("sub-two"))).andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(2, customers.count());
    }

    @Test
    void patchesOnlyTheFieldsProvided() throws Exception {
        mockMvc.perform(get("/api/customers/me").with(customer("sub-carol")));

        mockMvc.perform(patch("/api/customers/me")
                        .with(customer("sub-carol"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+44 20 7946 0000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+44 20 7946 0000"))
                // Omitted fields must survive - this is a PATCH, not a PUT.
                .andExpect(jsonPath("$.firstName").value("Test"));
    }

    @Test
    void omittingMarketingOptInDoesNotSilentlyOptOut() throws Exception {
        mockMvc.perform(patch("/api/customers/me")
                .with(customer("sub-dave"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"marketingOptIn\":true}"));

        mockMvc.perform(patch("/api/customers/me")
                        .with(customer("sub-dave"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"123\"}"))
                .andExpect(status().isOk())
                // Would be false if the DTO used a primitive boolean.
                .andExpect(jsonPath("$.marketingOptIn").value(true));
    }

    @Test
    void rejectsOverlongName() throws Exception {
        mockMvc.perform(patch("/api/customers/me")
                        .with(customer("sub-erin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("firstName"));
    }

    @Test
    void adminSearchIsForbiddenForPlainCustomers() throws Exception {
        // 403, not 401: the token is valid, the role is missing. If this ever returns
        // 200, @EnableMethodSecurity is missing and @PreAuthorize is being ignored.
        mockMvc.perform(get("/api/customers").with(customer("sub-frank")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSearchByEmail() throws Exception {
        mockMvc.perform(get("/api/customers/me").with(customer("sub-grace")));

        mockMvc.perform(get("/api/customers").param("email", "GRACE").with(admin("sub-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("sub-grace@test.local"));
    }

    @Test
    void adminGetOfUnknownCustomerIsNotFound() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", java.util.UUID.randomUUID())
                        .with(admin("sub-admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.correlationId").exists());
    }
}
