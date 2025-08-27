program zkpay_mobile.aleo {
    // Constants
    const MAX_SPLIT_RECIPIENTS: u8 = 4u8;
    const MIN_PAYMENT_AMOUNT: u64 = 1u64;
    const MAX_PAYMENT_AMOUNT: u64 = 1000000000u64; // 1B microcredits
    const ADMIN: address = aleo1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqanmpl0;

    // Core balance record for private payments
    record Balance {
        owner: address,
        amount: u64,
        nonce: field,
    }

    // Payment record for transaction outputs
    record Payment {
        owner: address,
        from: address,
        amount: u64,
        timestamp: u64,
        memo_hash: field,
    }

    // Zero-knowledge payment proof structure
    struct PaymentProof {
        amount_commitment: group,
        balance_commitment: group,
        nullifier: field,
        proof: field,
    }

    // Encrypted note for private memos
    struct EncryptedNote {
        recipient: address,
        encrypted_data: field,
        salt: field,
    }

    // Group payment splitting structure
    struct SplitPayment {
        total_amount: u64,
        recipient_count: u8,
        split_hash: field,
    }

    // Public mappings for deposit/withdrawal
    mapping public_balances: address => u64;
    mapping payment_nullifiers: field => bool;
    mapping total_supply: field => u64;

    // Private peer-to-peer payment
    transition send_private(
        balance: Balance,
        to: address,
        amount: u64
    ) -> (Balance, Payment) {
        // Validate inputs
        assert_eq(balance.owner, self.signer);
        assert(amount >= MIN_PAYMENT_AMOUNT);
        assert(amount <= MAX_PAYMENT_AMOUNT);
        assert(amount <= balance.amount);
        assert_neq(to, self.signer);

        // Create remaining balance for sender
        let remaining_balance: Balance = Balance {
            owner: self.signer,
            amount: balance.amount - amount,
            nonce: BHP256::hash_to_field(balance.nonce),
        };

        // Create payment record for recipient
        let payment: Payment = Payment {
            owner: to,
            from: self.signer,
            amount: amount,
            timestamp: 0u64, // Set in finalize if needed
            memo_hash: BHP256::hash_to_field(self.signer),
        };

        return (remaining_balance, payment);
    }

    // Group payment splitting - split into multiple single transfers
    transition split_payment_1(
        balance: Balance,
        recipient1: address,
        amount1: u64
    ) -> (Balance, Payment) {
        // Validate sender owns the balance
        assert_eq(balance.owner, self.signer);
        assert(amount1 >= MIN_PAYMENT_AMOUNT);
        assert(amount1 <= MAX_PAYMENT_AMOUNT);
        assert(amount1 <= balance.amount);
        assert_neq(recipient1, self.signer);

        // Create remaining balance
        let remaining_balance: Balance = Balance {
            owner: self.signer,
            amount: balance.amount - amount1,
            nonce: BHP256::hash_to_field(balance.nonce),
        };

        // Create payment record
        let payment: Payment = Payment {
            owner: recipient1,
            from: self.signer,
            amount: amount1,
            timestamp: 0u64,
            memo_hash: BHP256::hash_to_field(recipient1),
        };

        return (remaining_balance, payment);
    }

    // Second split payment for chaining
    transition split_payment_2(
        balance: Balance,
        recipient1: address,
        amount1: u64,
        recipient2: address,
        amount2: u64
    ) -> (Balance, Payment, Payment) {
        // Validate sender owns the balance
        assert_eq(balance.owner, self.signer);
        assert(amount1 >= MIN_PAYMENT_AMOUNT);
        assert(amount2 >= MIN_PAYMENT_AMOUNT);
        assert(amount1 <= MAX_PAYMENT_AMOUNT);
        assert(amount2 <= MAX_PAYMENT_AMOUNT);
        assert(amount1 + amount2 <= balance.amount);
        assert_neq(recipient1, self.signer);
        assert_neq(recipient2, self.signer);

        // Create remaining balance
        let remaining_balance: Balance = Balance {
            owner: self.signer,
            amount: balance.amount - amount1 - amount2,
            nonce: BHP256::hash_to_field(balance.nonce),
        };

        // Create payment records
        let payment1: Payment = Payment {
            owner: recipient1,
            from: self.signer,
            amount: amount1,
            timestamp: 0u64,
            memo_hash: BHP256::hash_to_field(recipient1),
        };

        let payment2: Payment = Payment {
            owner: recipient2,
            from: self.signer,
            amount: amount2,
            timestamp: 0u64,
            memo_hash: BHP256::hash_to_field(recipient2),
        };

        return (remaining_balance, payment1, payment2);
    }

    // Private deposit from public funds
    async transition deposit_private(public amount: u64) -> (Balance, Future) {
        // Validate deposit amount
        assert(amount >= MIN_PAYMENT_AMOUNT);
        assert(amount <= MAX_PAYMENT_AMOUNT);

        // Create private balance record
        let balance: Balance = Balance {
            owner: self.signer,
            amount: amount,
            nonce: BHP256::hash_to_field(self.signer),
        };

        // Finalize the deposit
        let f: Future = finalize_deposit(self.signer, amount);
        return (balance, f);
    }

    async function finalize_deposit(depositor: address, amount: u64) {
        // Check public balance
        let public_balance: u64 = Mapping::get_or_use(public_balances, depositor, 0u64);
        assert(public_balance >= amount);

        // Deduct from public balance
        Mapping::set(public_balances, depositor, public_balance - amount);

        // Update total supply tracking
        let supply: u64 = Mapping::get_or_use(total_supply, 1field, 0u64);
        Mapping::set(total_supply, 1field, supply + amount);
    }

    // Private withdrawal to public funds
    async transition withdraw_private(
        balance: Balance,
        public amount: u64
    ) -> (Balance, Future) {
        // Validate withdrawal
        assert_eq(balance.owner, self.signer);
        assert(amount >= MIN_PAYMENT_AMOUNT);
        assert(amount <= balance.amount);

        // Create remaining private balance
        let remaining_balance: Balance = Balance {
            owner: self.signer,
            amount: balance.amount - amount,
            nonce: BHP256::hash_to_field(balance.nonce),
        };

        // Finalize the withdrawal
        let f: Future = finalize_withdraw(self.signer, amount);
        return (remaining_balance, f);
    }

    async function finalize_withdraw(withdrawer: address, amount: u64) {
        // Add to public balance
        let public_balance: u64 = Mapping::get_or_use(public_balances, withdrawer, 0u64);
        assert(public_balance + amount >= public_balance); // Overflow check
        Mapping::set(public_balances, withdrawer, public_balance + amount);

        // Update total supply tracking
        let supply: u64 = Mapping::get_or_use(total_supply, 1field, 0u64);
        assert(supply >= amount);
        Mapping::set(total_supply, 1field, supply - amount);
    }

    // Zero-knowledge payment verification
    transition verify_payment(proof: PaymentProof) -> bool {
        // Verify the proof components are valid
        assert_neq(proof.amount_commitment, 0group);
        assert_neq(proof.balance_commitment, 0group);
        assert_neq(proof.nullifier, 0field);
        assert_neq(proof.proof, 0field);

        // Create verification hash using individual fields
        let hash1: field = BHP256::hash_to_field(proof.amount_commitment);
        let hash2: field = BHP256::hash_to_field(proof.balance_commitment);
        let hash3: field = BHP256::hash_to_field(proof.nullifier);
        let hash4: field = BHP256::hash_to_field(proof.proof);
        
        // Combine hashes
        let verification_hash: field = hash1 + hash2 + hash3 + hash4;

        // Simple verification - in production this would use more complex ZK verification
        let is_valid: bool = verification_hash != 0field;
        
        return is_valid;
    }

    // Convert payment to balance (claim payment)
    transition claim_payment(payment: Payment) -> Balance {
        // Validate ownership
        assert_eq(payment.owner, self.signer);
        assert(payment.amount > 0u64);

        // Create balance from payment
        let balance: Balance = Balance {
            owner: self.signer,
            amount: payment.amount,
            nonce: BHP256::hash_to_field(payment.from),
        };

        return balance;
    }

    // Merge multiple balances into one
    transition merge_balances(
        balance1: Balance,
        balance2: Balance
    ) -> Balance {
        // Validate ownership
        assert_eq(balance1.owner, self.signer);
        assert_eq(balance2.owner, self.signer);

        // Check for overflow
        let total_amount: u64 = balance1.amount + balance2.amount;
        assert(total_amount >= balance1.amount);
        assert(total_amount >= balance2.amount);

        // Create merged balance
        let merged_balance: Balance = Balance {
            owner: self.signer,
            amount: total_amount,
            nonce: BHP256::hash_to_field(balance1.nonce),
        };

        return merged_balance;
    }

    // Create encrypted note for payment memo
    transition create_encrypted_note(
        recipient: address,
        message: field,
        salt: field
    ) -> EncryptedNote {
        // Create encryption hash using individual fields
        let hash1: field = BHP256::hash_to_field(message);
        let hash2: field = BHP256::hash_to_field(salt);
        let hash3: field = BHP256::hash_to_field(self.signer);
        let encrypted_data: field = hash1 + hash2 + hash3;

        let note: EncryptedNote = EncryptedNote {
            recipient: recipient,
            encrypted_data: encrypted_data,
            salt: salt,
        };

        return note;
    }

    // Admin function to initialize public balance (for testing/demo)
    async transition initialize_balance(
        public user: address,
        public amount: u64
    ) -> Future {
        // Only admin can initialize
        assert_eq(self.signer, ADMIN);
        assert(amount > 0u64);

        let f: Future = finalize_init_balance(user, amount);
        return f;
    }

    async function finalize_init_balance(user: address, amount: u64) {
        // Set initial public balance
        Mapping::set(public_balances, user, amount);
    }

    // View public balance (for UI integration)
    async transition get_public_balance(public user: address) -> Future {
        let f: Future = finalize_get_balance(user);
        return f;
    }

    async function finalize_get_balance(user: address) {
        // This would typically return the balance, but Leo async functions don't return values
        // The balance can be queried directly from the mapping in the frontend
        let balance: u64 = Mapping::get_or_use(public_balances, user, 0u64);
    }
}