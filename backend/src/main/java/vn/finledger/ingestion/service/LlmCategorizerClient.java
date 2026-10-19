package vn.finledger.ingestion.service;

import vn.finledger.ledger.domain.Account;

import java.util.List;
import java.util.Optional;

public interface LlmCategorizerClient {

    Optional<CategorizationResult> categorizeWithAi(
            String sanitizedContent,
            String transferType,
            List<Account> candidateAccounts
    );
}
