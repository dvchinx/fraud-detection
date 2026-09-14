package com.florez.backend.transaction;

import tools.jackson.databind.ObjectMapper;
import com.florez.backend.TestcontainersConfiguration;
import com.florez.backend.auth.JwtService;
import com.florez.backend.user.User;
import com.florez.backend.user.UserRepository;
import com.florez.backend.user.UserRole;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Apunta el cliente de ML a un puerto cerrado a proposito: estos tests verifican
 * las reglas de negocio, asi que el modelo debe quedar siempre fuera de juego
 * (fail-open). Sin esto el resultado cambia segun si el dev tiene levantado el
 * servicio de ML en localhost:8000.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "ml.service.url=http://localhost:9")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;
    private UUID userId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("tx-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFullName("Tx User");
        user.setRole(UserRole.USER);
        user = userRepository.save(user);

        userId = user.getId();
        token = jwtService.generateToken(user);
    }

    private TransactionResponse fetchTransaction(UUID id) throws Exception {
        String responseBody = mockMvc.perform(get("/transactions/" + id)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(responseBody, TransactionResponse.class);
    }

    private TransactionResponse createTransaction(BigDecimal amount, String country) throws Exception {
        TransactionRequest request = new TransactionRequest(userId, amount, "USD", "Amazon", country);

        String responseBody = mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        UUID id = objectMapper.readValue(responseBody, TransactionResponse.class).id();

        return Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> fetchTransaction(id), response -> response.status() != TransactionStatus.PENDING);
    }

    @Test
    void createAndGetTransaction() throws Exception {
        TransactionResponse created = createTransaction(new BigDecimal("100.50"), "US");

        org.junit.jupiter.api.Assertions.assertEquals(TransactionStatus.APPROVED, created.status());
        org.junit.jupiter.api.Assertions.assertEquals("Ninguna regla activada", created.reason());
        org.junit.jupiter.api.Assertions.assertNotNull(created.decidedAt());
        org.junit.jupiter.api.Assertions.assertTrue(created.ruleOutcomes().isEmpty());

        mockMvc.perform(get("/transactions/" + created.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("Amazon"));
    }

    @Test
    void getTransaction_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/transactions/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTransaction_notFound_returnsNotFound() throws Exception {
        mockMvc.perform(get("/transactions/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void highAmountTransaction_isBlocked() throws Exception {
        TransactionResponse response = createTransaction(new BigDecimal("15000"), "US");

        org.junit.jupiter.api.Assertions.assertEquals(TransactionStatus.BLOCKED, response.status());
        org.junit.jupiter.api.Assertions.assertTrue(response.reason().contains("bloqueo"));
        org.junit.jupiter.api.Assertions.assertEquals(1, response.ruleOutcomes().size());
        org.junit.jupiter.api.Assertions.assertEquals("HighAmountRule", response.ruleOutcomes().get(0).rule());
    }

    @Test
    void confirmFraud_persistsGroundTruthLabel() throws Exception {
        TransactionResponse created = createTransaction(new BigDecimal("50.00"), "US");

        String responseBody = mockMvc.perform(patch("/transactions/" + created.id() + "/confirmed-fraud")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedFraud\": true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        TransactionResponse updated = objectMapper.readValue(responseBody, TransactionResponse.class);
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, updated.confirmedFraud());
    }

    @Test
    void rapidTransactions_triggerVelocityReview() throws Exception {
        for (int i = 0; i < 5; i++) {
            createTransaction(new BigDecimal("10.00"), "US");
        }

        TransactionResponse sixth = createTransaction(new BigDecimal("10.00"), "US");

        org.junit.jupiter.api.Assertions.assertEquals(TransactionStatus.REVIEW, sixth.status());
        org.junit.jupiter.api.Assertions.assertTrue(sixth.reason().contains("transacciones"));
    }

    @Test
    void countryChange_triggersGeoReview() throws Exception {
        createTransaction(new BigDecimal("10.00"), "US");
        TransactionResponse second = createTransaction(new BigDecimal("10.00"), "CA");

        org.junit.jupiter.api.Assertions.assertEquals(TransactionStatus.REVIEW, second.status());
        org.junit.jupiter.api.Assertions.assertTrue(second.reason().contains("País"));
    }

    @Test
    void sameCountry_doesNotTriggerGeoReviewAgainstItself() throws Exception {
        createTransaction(new BigDecimal("10.00"), "US");
        TransactionResponse second = createTransaction(new BigDecimal("10.00"), "US");

        org.junit.jupiter.api.Assertions.assertEquals(TransactionStatus.APPROVED, second.status());
        org.junit.jupiter.api.Assertions.assertEquals("Ninguna regla activada", second.reason());
    }
}
