package com.benmochen.portfolio.transaction;

import com.benmochen.portfolio.account.AccountRepository;
import com.benmochen.portfolio.common.NotFoundException;
import com.benmochen.portfolio.security.CurrentUser;
import com.benmochen.portfolio.transaction.TransactionDtos.TransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CurrentUser currentUser;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              CurrentUser currentUser) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findByAccount(Long accountId) {
        if (!accountRepository.existsByIdAndUserId(accountId, currentUser.requireId())) {
            throw NotFoundException.of("Account", accountId);
        }
        return transactionRepository
                .findLedger(accountId)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }
}
