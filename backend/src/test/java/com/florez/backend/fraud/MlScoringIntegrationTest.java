package com.florez.backend.fraud;

import com.florez.backend.TestcontainersConfiguration;
import com.florez.backend.auth.JwtService;
import com.florez.backend.transaction.TransactionRequest;
import com.florez.backend.transaction.TransactionResponse;
import com.florez.backend.transaction.TransactionStatus;
import com.florez.backend.user.User;
import com.florez.backend.user.UserRepository;
import com.florez.backend.user.UserRole;
import com.sun.net.httpserver.HttpServer;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre el camino feliz del servicio de ML, que el resto de tests no ejercita:
 * ahi el servicio esta caido y la regla se omite (fail-open).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MlScoringIntegrationTest {

    private static final String SCORE_RESPONSE = """
            {
              "riskScore": 0.95,
              "modelVersion": "logreg-test",
              "baseValue": -0.94,
              "topFactors": [
                {"feature": "amount", "contribution": 2.5},
                {"feature": "hour_of_day", "contribution": -0.3}
              ]
            }
            """;

    private static final HttpServer ML_STUB = startMlStub();

    private static HttpServer startMlStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/score", exchange -> {
                byte[] body = SCORE_RESPONSE.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo levantar el stub del servicio de ML", e);
        }
    }

    @DynamicPropertySource
    static void mlServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("ml.service.url", () -> "http://localhost:" + ML_STUB.getAddress().getPort());
    }

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
        user.setEmail("ml-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFullName("Ml User");
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

    @Test
    void highRiskScore_blocksAndPersistsShapExplanation() throws Exception {
        // Monto bajo y usuario nuevo: ninguna otra regla puede activarse, asi que
        // la decision viene necesariamente del modelo.
        TransactionRequest request = new TransactionRequest(userId, new BigDecimal("50.00"), "USD", "Amazon", "US");

        String responseBody = mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        UUID id = objectMapper.readValue(responseBody, TransactionResponse.class).id();

        TransactionResponse evaluated = Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> fetchTransaction(id), response -> response.status() != TransactionStatus.PENDING);

        Assertions.assertEquals(TransactionStatus.BLOCKED, evaluated.status());
        Assertions.assertEquals(0.95, evaluated.mlRiskScore());
        Assertions.assertEquals("logreg-test", evaluated.mlModelVersion());
        Assertions.assertEquals(-0.94, evaluated.mlBaseValue());
        Assertions.assertEquals(2, evaluated.mlTopFactors().size());
        Assertions.assertEquals("amount", evaluated.mlTopFactors().get(0).feature());
        Assertions.assertEquals(2.5, evaluated.mlTopFactors().get(0).contribution());
        Assertions.assertEquals(1, evaluated.ruleOutcomes().size());
        Assertions.assertEquals("MlScoringRule", evaluated.ruleOutcomes().get(0).rule());
    }
}
