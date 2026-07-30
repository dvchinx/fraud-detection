package com.florez.backend.transaction;

import tools.jackson.databind.ObjectMapper;
import com.florez.backend.TestcontainersConfiguration;
import com.florez.backend.auth.JwtService;
import com.florez.backend.user.User;
import com.florez.backend.user.UserRepository;
import com.florez.backend.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
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

    @Test
    void createAndGetTransaction() throws Exception {
        TransactionRequest request = new TransactionRequest(userId, new BigDecimal("100.50"), "USD", "Amazon", "US");

        String responseBody = mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        TransactionResponse created = objectMapper.readValue(responseBody, TransactionResponse.class);

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
}
