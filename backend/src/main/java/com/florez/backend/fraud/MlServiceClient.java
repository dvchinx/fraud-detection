package com.florez.backend.fraud;

import com.florez.backend.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
public class MlServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MlServiceClient.class);

    private final RestClient mlServiceRestClient;

    public MlServiceClient(RestClient mlServiceRestClient) {
        this.mlServiceRestClient = mlServiceRestClient;
    }

    public Optional<MlScoreResponse> score(Transaction transaction) {
        MlScoreRequest request = new MlScoreRequest(
                transaction.getAmount(),
                transaction.getMerchant(),
                transaction.getCountry(),
                transaction.getCreatedAt()
        );

        try {
            MlScoreResponse response = mlServiceRestClient.post()
                    .uri("/score")
                    .body(request)
                    .retrieve()
                    .body(MlScoreResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException e) {
            log.warn("Servicio de ML no disponible, se omite su score para la transacción {}: {}",
                    transaction.getId(), e.getMessage());
            return Optional.empty();
        }
    }
}
