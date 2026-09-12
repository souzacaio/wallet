package br.com.datum.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWalletRequest(
        @NotBlank(message = "holderName is required")
        @Size(max = 255, message = "holderName must be at most 255 characters")
        String holderName
) {
}
