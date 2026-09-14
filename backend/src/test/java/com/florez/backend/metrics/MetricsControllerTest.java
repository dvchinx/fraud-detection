package com.florez.backend.metrics;

import tools.jackson.databind.ObjectMapper;
import com.florez.backend.TestcontainersConfiguration;
import com.florez.backend.auth.JwtService;
import com.florez.backend.transaction.TransactionRequest;
import com.florez.backend.transaction.TransactionResponse;
import com.florez.backend.transaction.TransactionStatus;
import com.florez.backend.user.User;
import com.florez.backend.user.UserRepository;
import com.florez.backend.user.UserRole;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MetricsControllerTest {

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
        user.setEmail("metrics-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFullName("Metrics User");
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

    private TransactionResponse createTransaction(BigDecimal amount) throws Exception {
        TransactionRequest request = new TransactionRequest(userId, amount, "USD", "Amazon", "US");

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
    void summary_reflectsRecentDecisions() throws Exception {
        createTransaction(new BigDecimal("50.00"));
        TransactionResponse blocked = createTransaction(new BigDecimal("15000"));

        String responseBody = mockMvc.perform(get("/metrics/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        MetricsSummaryResponse summary = objectMapper.readValue(responseBody, MetricsSummaryResponse.class);

        Assertions.assertTrue(summary.totalTransactions() >= 2);
        Assertions.assertTrue(summary.statusCounts().getOrDefault("BLOCKED", 0L) >= 1);
        Assertions.assertTrue(summary.ruleTriggerCounts().getOrDefault("HighAmountRule", 0L) >= 1);
        Assertions.assertEquals(TransactionStatus.BLOCKED, blocked.status());
    }

    @Test
    void summary_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/metrics/summary"))
                .andExpect(status().isUnauthorized());
    }
}
