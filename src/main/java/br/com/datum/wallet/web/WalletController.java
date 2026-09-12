package br.com.datum.wallet.web;

import br.com.datum.wallet.domain.Transaction;
import br.com.datum.wallet.domain.Wallet;
import br.com.datum.wallet.service.TransactionService;
import br.com.datum.wallet.service.WalletService;
import br.com.datum.wallet.service.dto.TransactionResult;
import br.com.datum.wallet.web.dto.CreateTransactionRequest;
import br.com.datum.wallet.web.dto.CreateTransactionResponse;
import br.com.datum.wallet.web.dto.CreateWalletRequest;
import br.com.datum.wallet.web.dto.PagedResponse;
import br.com.datum.wallet.web.dto.TransactionResponse;
import br.com.datum.wallet.web.dto.WalletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/wallets")
@Validated
public class WalletController {

    private final WalletService walletService;
    private final TransactionService transactionService;

    public WalletController(WalletService walletService, TransactionService transactionService) {
        this.walletService = walletService;
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody CreateWalletRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        Wallet wallet = walletService.create(request.holderName());
        URI location = uriBuilder.path("/wallets/{id}").buildAndExpand(wallet.getId()).toUri();
        return ResponseEntity.created(location).body(WalletResponse.from(wallet));
    }

    @GetMapping("/{id}")
    public WalletResponse get(@PathVariable UUID id) {
        return WalletResponse.from(walletService.getById(id));
    }

    @PostMapping("/{id}/transactions")
    public ResponseEntity<CreateTransactionResponse> createTransaction(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") @NotBlank(message = "Idempotency-Key header is required") String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionResult result = transactionService.apply(id, request.type(), request.amount(), idempotencyKey);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        CreateTransactionResponse body = new CreateTransactionResponse(
                result.message(), result.replayed(), TransactionResponse.from(result.transaction()));
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/{id}/transactions")
    public PagedResponse<TransactionResponse> listTransactions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Transaction> result = transactionService.list(id, from, to, pageable);
        return PagedResponse.from(result, TransactionResponse::from);
    }
}
