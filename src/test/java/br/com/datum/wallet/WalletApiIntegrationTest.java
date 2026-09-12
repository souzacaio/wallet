package br.com.datum.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class WalletApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private HttpHeaders authJsonHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", API_KEY);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private HttpHeaders authHeaders() {
        return authJsonHeaders(null);
    }

    private String createWallet() {
        HttpEntity<String> request = new HttpEntity<>("{\"holderName\":\"Alice\"}", authHeaders());
        ResponseEntity<JsonNode> response = rest.postForEntity("/wallets", request, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asText();
    }

    @Test
    void fullHappyPathFlow() {
        String walletId = createWallet();

        // credit 100.00
        HttpEntity<String> credit = new HttpEntity<>("{\"type\":\"CREDIT\",\"amount\":100.00}", authJsonHeaders("credit-1"));
        ResponseEntity<JsonNode> creditResp = rest.postForEntity("/wallets/{id}/transactions", credit, JsonNode.class, walletId);
        assertThat(creditResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(creditResp.getBody().get("balanceAfter").decimalValue()).isEqualByComparingTo("100.00");

        // debit 30.00
        HttpEntity<String> debit = new HttpEntity<>("{\"type\":\"DEBIT\",\"amount\":30.00}", authJsonHeaders("debit-1"));
        ResponseEntity<JsonNode> debitResp = rest.postForEntity("/wallets/{id}/transactions", debit, JsonNode.class, walletId);
        assertThat(debitResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(debitResp.getBody().get("balanceAfter").decimalValue()).isEqualByComparingTo("70.00");

        // balance check
        ResponseEntity<JsonNode> walletResp = rest.exchange("/wallets/{id}", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), JsonNode.class, walletId);
        assertThat(walletResp.getBody().get("balance").decimalValue()).isEqualByComparingTo("70.00");

        // statement lists both operations
        ResponseEntity<JsonNode> statement = rest.exchange("/wallets/{id}/transactions", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), JsonNode.class, walletId);
        assertThat(statement.getBody().get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void insufficientBalanceReturns422() {
        String walletId = createWallet();
        HttpEntity<String> debit = new HttpEntity<>("{\"type\":\"DEBIT\",\"amount\":10.00}", authJsonHeaders("over-1"));
        ResponseEntity<JsonNode> resp = rest.postForEntity("/wallets/{id}/transactions", debit, JsonNode.class, walletId);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().get("code").asText()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    void replayWithSameKeyReturnsSameResult() {
        String walletId = createWallet();
        HttpEntity<String> credit = new HttpEntity<>("{\"type\":\"CREDIT\",\"amount\":25.00}", authJsonHeaders("dup-1"));

        ResponseEntity<JsonNode> first = rest.postForEntity("/wallets/{id}/transactions", credit, JsonNode.class, walletId);
        ResponseEntity<JsonNode> second = rest.postForEntity("/wallets/{id}/transactions", credit, JsonNode.class, walletId);

        assertThat(first.getBody().get("id").asText()).isEqualTo(second.getBody().get("id").asText());

        ResponseEntity<JsonNode> walletResp = rest.exchange("/wallets/{id}", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), JsonNode.class, walletId);
        assertThat(walletResp.getBody().get("balance").decimalValue()).isEqualByComparingTo("25.00");
    }

    @Test
    void reusingKeyForDifferentPayloadReturns409() {
        String walletId = createWallet();
        HttpEntity<String> first = new HttpEntity<>("{\"type\":\"CREDIT\",\"amount\":10.00}", authJsonHeaders("conf-1"));
        rest.postForEntity("/wallets/{id}/transactions", first, JsonNode.class, walletId);

        HttpEntity<String> conflicting = new HttpEntity<>("{\"type\":\"CREDIT\",\"amount\":99.00}", authJsonHeaders("conf-1"));
        ResponseEntity<JsonNode> resp = rest.postForEntity("/wallets/{id}/transactions", conflicting, JsonNode.class, walletId);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unknownWalletReturns404() {
        ResponseEntity<JsonNode> resp = rest.exchange("/wallets/{id}", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), JsonNode.class, "00000000-0000-0000-0000-000000000000");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void missingApiKeyReturns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"holderName\":\"Bob\"}", headers);
        ResponseEntity<JsonNode> resp = rest.postForEntity("/wallets", request, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void nonPositiveAmountReturns400() {
        String walletId = createWallet();
        HttpEntity<String> bad = new HttpEntity<>("{\"type\":\"CREDIT\",\"amount\":0}", authJsonHeaders("bad-1"));
        ResponseEntity<JsonNode> resp = rest.postForEntity("/wallets/{id}/transactions", bad, JsonNode.class, walletId);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
