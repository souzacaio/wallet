package br.com.datum.wallet.service;

import br.com.datum.wallet.domain.Wallet;
import br.com.datum.wallet.domain.exception.WalletNotFoundException;
import br.com.datum.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public Wallet create(String holderName) {
        return walletRepository.save(Wallet.create(holderName));
    }

    @Transactional(readOnly = true)
    public Wallet getById(UUID id) {
        return walletRepository.findById(id)
                .orElseThrow(() -> new WalletNotFoundException(id));
    }
}
