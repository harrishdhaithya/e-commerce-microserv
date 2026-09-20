package com.ecommerce.customer.api;

import com.ecommerce.customer.repository.AddressRepository;
import com.ecommerce.customer.repository.CustomerRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AddressControllerTest {

    private static final String VALID_ADDRESS = """
            {
              "label": "Home",
              "recipientName": "Alice Smith",
              "line1": "1 Alice Street",
              "city": "London",
              "postalCode": "SW1A 1AA",
              "countryCode": "gb"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private AddressRepository addresses;

    @BeforeEach
    void setUp() {
        addresses.deleteAll();
        customers.deleteAll();
    }

    private static RequestPostProcessor as(String sub) {
        return jwt()
                .jwt(builder -> builder.subject(sub).claim("email", sub + "@test.local"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private String createAddress(String sub, String body) throws Exception {
        String response = mockMvc.perform(post("/api/customers/me/addresses")
                        .with(as(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/customers/me/addresses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsAddressAndReturnsLocation() throws Exception {
        mockMvc.perform(post("/api/customers/me/addresses")
                        .with(as("sub-alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ADDRESS))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.line1").value("1 Alice Street"))
                // Entity uppercases on the way in, so "gb" is stored as "GB".
                .andExpect(jsonPath("$.countryCode").value("GB"));
    }

    @Test
    void createsTheOwningCustomerIfTheyDoNotExistYet() throws Exception {
        // The customer never called /me first. Provisioning still has to happen.
        createAddress("sub-new", VALID_ADDRESS);

        assertEquals(1, customers.count());
    }

    @Test
    void listsOnlyOwnAddresses() throws Exception {
        createAddress("sub-alice", VALID_ADDRESS);
        createAddress("sub-bob", VALID_ADDRESS);

        mockMvc.perform(get("/api/customers/me/addresses").with(as("sub-alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void cannotReadAnotherCustomersAddress() throws Exception {
        String aliceAddress = createAddress("sub-alice", VALID_ADDRESS);

        // 404 rather than 403: a 403 would confirm the id exists.
        mockMvc.perform(get("/api/customers/me/addresses/{id}", aliceAddress).with(as("sub-bob")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotDeleteAnotherCustomersAddress() throws Exception {
        String aliceAddress = createAddress("sub-alice", VALID_ADDRESS);

        mockMvc.perform(delete("/api/customers/me/addresses/{id}", aliceAddress).with(as("sub-bob")))
                .andExpect(status().isNotFound());

        // And it is still there.
        assertEquals(1, addresses.count());
    }

    @Test
    void cannotReplaceAnotherCustomersAddress() throws Exception {
        String aliceAddress = createAddress("sub-alice", VALID_ADDRESS);

        mockMvc.perform(put("/api/customers/me/addresses/{id}", aliceAddress)
                        .with(as("sub-bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ADDRESS))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesOwnAddress() throws Exception {
        String id = createAddress("sub-alice", VALID_ADDRESS);

        mockMvc.perform(delete("/api/customers/me/addresses/{id}", id).with(as("sub-alice")))
                .andExpect(status().isNoContent());

        assertEquals(0, addresses.count());
    }

    @Test
    void rejectsInvalidCountryCode() throws Exception {
        mockMvc.perform(post("/api/customers/me/addresses")
                        .with(as("sub-alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ADDRESS.replace("\"gb\"", "\"GBR\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("countryCode"));
    }

    @Test
    void rejectsMissingRequiredField() throws Exception {
        mockMvc.perform(post("/api/customers/me/addresses")
                        .with(as("sub-alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"A\",\"city\":\"London\","
                                + "\"postalCode\":\"SW1\",\"countryCode\":\"GB\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("line1"));
    }

    @Test
    void settingANewDefaultClearsThePrevious() throws Exception {
        String first = createAddress("sub-alice", VALID_ADDRESS);
        String second = createAddress("sub-alice",
                VALID_ADDRESS.replace("1 Alice Street", "2 Second Avenue"));

        mockMvc.perform(put("/api/customers/me/addresses/{id}/default-shipping", first)
                        .with(as("sub-alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultShipping").value(true));

        mockMvc.perform(put("/api/customers/me/addresses/{id}/default-shipping", second)
                        .with(as("sub-alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultShipping").value(true));

        JsonNode list = json.readTree(mockMvc.perform(
                        get("/api/customers/me/addresses").with(as("sub-alice")))
                .andReturn().getResponse().getContentAsString());

        long defaults = 0;
        for (JsonNode node : list) {
            if (node.get("defaultShipping").asBoolean()) {
                defaults++;
            }
        }
        assertEquals(1, defaults, "exactly one default shipping address must remain");
    }

    @Test
    void settingDefaultOnAnotherCustomersAddressChangesNothing() throws Exception {
        String aliceAddress = createAddress("sub-alice", VALID_ADDRESS);
        String bobAddress = createAddress("sub-bob", VALID_ADDRESS);
        mockMvc.perform(put("/api/customers/me/addresses/{id}/default-shipping", bobAddress)
                .with(as("sub-bob")));

        mockMvc.perform(put("/api/customers/me/addresses/{id}/default-shipping", aliceAddress)
                        .with(as("sub-bob")))
                .andExpect(status().isNotFound());

        // The rollback matters: the clear ran before the 404 was raised, so without
        // it Bob would be left with no default at all.
        mockMvc.perform(get("/api/customers/me/addresses").with(as("sub-bob")))
                .andExpect(jsonPath("$[0].defaultShipping").value(true));
    }

    @Test
    void unknownAddressIsNotFound() throws Exception {
        mockMvc.perform(get("/api/customers/me/addresses/{id}", UUID.randomUUID())
                        .with(as("sub-alice")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").exists());
    }
}
