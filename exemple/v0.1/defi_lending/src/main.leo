program defi_lending.aleo {
    // Constants
    const ADMIN: address = aleo1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqanmpl0;
    const MIN_COLLATERAL: u64 = 1000u64;
    const MAX_LOAN_RATIO: u64 = 8000u64; // 80% max LTV
    const LIQUIDATION_BONUS: u64 = 500u64; // 5% bonus
    const INTEREST_PRECISION: u64 = 10000u64; // Basis points
    const SECONDS_PER_YEAR: u64 = 31536000u64;
    
    // Structs
    struct LoanData {
        borrower: address,
        collateral_amount: u64,
        borrowed_amount: u64,
        interest_rate: u64,
        last_update: u32,
        is_active: bool,
    }
    
    // Records
    record Collateral {
        owner: address,
        token_type: u8,
        amount: u64,
        loan_id: u64,
    }
    
    record LoanReceipt {
        owner: address,
        loan_id: u64,
        borrowed_amount: u64,
        collateral_value: u64,
    }
    
    // Mappings
    mapping loans: u64 => LoanData;
    mapping collateral_prices: u8 => u64;
    mapping total_borrowed: u8 => u64;
    mapping liquidation_threshold: u8 => u64;
    mapping interest_rates: u8 => u64;
    mapping next_loan_id: u8 => u64;
    mapping paused: u8 => bool;
    
    // Deposit collateral privately
    transition deposit_collateral(token_type: u8, amount: u64) -> Collateral {
        assert(amount >= MIN_COLLATERAL);
        
        return Collateral {
            owner: self.signer,
            token_type,
            amount,
            loan_id: 0u64, // Not assigned to loan yet
        };
    }
    
    // Borrow against collateral
    async transition borrow(
        collateral: Collateral, 
        borrow_amount: u64
    ) -> (LoanReceipt, Future) {
        assert_eq(collateral.owner, self.signer);
        assert(borrow_amount > 0u64);
        assert_eq(collateral.loan_id, 0u64); // Must be unencumbered
        
        let f: Future = finalize_borrow(
            collateral.token_type, 
            collateral.amount, 
            borrow_amount, 
            self.signer
        );
        
        // Create loan receipt (loan_id will be set in finalize)
        let receipt: LoanReceipt = LoanReceipt {
            owner: self.signer,
            loan_id: 1u64, // Placeholder, actual ID set in finalize
            borrowed_amount: borrow_amount,
            collateral_value: collateral.amount,
        };
        
        return (receipt, f);
    }
    
    async function finalize_borrow(
        token_type: u8, 
        collateral_amount: u64, 
        borrow_amount: u64, 
        borrower: address
    ) {
        // Check if system is paused
        let is_paused: bool = Mapping::get_or_use(paused, 0u8, false);
        assert(!is_paused);
        
        // Get collateral price
        let price: u64 = Mapping::get_or_use(collateral_prices, token_type, 0u64);
        assert(price > 0u64);
        
        // Calculate collateral value in USD
        let collateral_value: u64 = collateral_amount * price;
        
        // Check loan-to-value ratio
        let max_borrow: u64 = collateral_value * MAX_LOAN_RATIO / 10000u64;
        assert(borrow_amount <= max_borrow);
        
        // Get next loan ID
        let loan_id: u64 = Mapping::get_or_use(next_loan_id, 0u8, 1u64);
        Mapping::set(next_loan_id, 0u8, loan_id + 1u64);
        
        // Get interest rate for token type
        let interest_rate: u64 = Mapping::get_or_use(interest_rates, token_type, 1000u64); // 10% default
        
        // Create loan data
        let loan_data: LoanData = LoanData {
            borrower,
            collateral_amount,
            borrowed_amount: borrow_amount,
            interest_rate,
            last_update: block.height,
            is_active: true,
        };
        
        // Store loan
        Mapping::set(loans, loan_id, loan_data);
        
        // Update total borrowed
        let total: u64 = Mapping::get_or_use(total_borrowed, token_type, 0u64);
        Mapping::set(total_borrowed, token_type, total + borrow_amount);
    }
    
    // Repay loan and get collateral back
    async transition repay_loan(
        receipt: LoanReceipt, 
        payment: u64
    ) -> (Collateral, Future) {
        assert_eq(receipt.owner, self.signer);
        assert(payment > 0u64);
        
        let f: Future = finalize_repay(receipt.loan_id, payment, self.signer);
        
        // Return collateral (token_type will be retrieved in finalize)
        let collateral: Collateral = Collateral {
            owner: self.signer,
            token_type: 1u8, // Placeholder
            amount: receipt.collateral_value,
            loan_id: 0u64,
        };
        
        return (collateral, f);
    }
    
    async function finalize_repay(loan_id: u64, payment: u64, repayer: address) {
        // Get loan data
        let loan: LoanData = Mapping::get(loans, loan_id);
        assert(loan.is_active);
        assert_eq(loan.borrower, repayer);
        
        // Calculate accrued interest
        let blocks_elapsed: u32 = block.height - loan.last_update;
        let interest: u64 = loan.borrowed_amount * loan.interest_rate * (blocks_elapsed as u64) 
                           / (INTEREST_PRECISION * SECONDS_PER_YEAR);
        let total_owed: u64 = loan.borrowed_amount + interest;
        
        assert(payment >= total_owed);
        
        // Mark loan as inactive
        let updated_loan: LoanData = LoanData {
            borrower: loan.borrower,
            collateral_amount: loan.collateral_amount,
            borrowed_amount: 0u64,
            interest_rate: loan.interest_rate,
            last_update: block.height,
            is_active: false,
        };
        
        Mapping::set(loans, loan_id, updated_loan);
    }
    
    // Liquidate undercollateralized loan
    async transition liquidate_loan(
        public loan_id: u64, 
        public payment: u64
    ) -> (Collateral, Future) {
        assert(payment > 0u64);
        
        let f: Future = finalize_liquidate(loan_id, payment, self.signer);
        
        // Liquidator gets collateral (details set in finalize)
        let collateral: Collateral = Collateral {
            owner: self.signer,
            token_type: 1u8, // Set in finalize
            amount: 1u64, // Set in finalize
            loan_id: 0u64,
        };
        
        return (collateral, f);
    }
    
    async function finalize_liquidate(
        loan_id: u64, 
        payment: u64, 
        liquidator: address
    ) {
        // Get loan data
        let loan: LoanData = Mapping::get(loans, loan_id);
        assert(loan.is_active);
        
        // Anti-self-liquidation protection
        assert_neq(loan.borrower, liquidator);
        
        // Calculate current debt with interest
        let blocks_elapsed: u32 = block.height - loan.last_update;
        let interest: u64 = loan.borrowed_amount * loan.interest_rate * (blocks_elapsed as u64) 
                           / (INTEREST_PRECISION * SECONDS_PER_YEAR);
        let total_debt: u64 = loan.borrowed_amount + interest;
        
        // Get current collateral price (assume token_type 1 for now)
        let price: u64 = Mapping::get_or_use(collateral_prices, 1u8, 0u64);
        assert(price > 0u64);
        
        let collateral_value: u64 = loan.collateral_amount * price;
        
        // Get liquidation threshold
        let threshold: u64 = Mapping::get_or_use(liquidation_threshold, 1u8, 8500u64); // 85%
        
        // Check if loan is undercollateralized
        let health_ratio: u64 = collateral_value * 10000u64 / total_debt;
        assert(health_ratio < threshold);
        
        // Verify payment covers debt
        assert(payment >= total_debt);
        
        // Calculate liquidation bonus
        let bonus_amount: u64 = loan.collateral_amount * LIQUIDATION_BONUS / 10000u64;
        let liquidator_reward: u64 = loan.collateral_amount + bonus_amount;
        
        // Mark loan as liquidated
        let liquidated_loan: LoanData = LoanData {
            borrower: loan.borrower,
            collateral_amount: 0u64,
            borrowed_amount: 0u64,
            interest_rate: loan.interest_rate,
            last_update: block.height,
            is_active: false,
        };
        
        Mapping::set(loans, loan_id, liquidated_loan);
    }
    
    // Update interest for a loan
    async transition update_interest(public loan_id: u64) -> Future {
        let f: Future = finalize_update_interest(loan_id);
        return f;
    }
    
    async function finalize_update_interest(loan_id: u64) {
        let loan: LoanData = Mapping::get(loans, loan_id);
        assert(loan.is_active);
        
        // Calculate accrued interest
        let blocks_elapsed: u32 = block.height - loan.last_update;
        let interest: u64 = loan.borrowed_amount * loan.interest_rate * (blocks_elapsed as u64) 
                           / (INTEREST_PRECISION * SECONDS_PER_YEAR);
        
        // Update loan with accrued interest
        let updated_loan: LoanData = LoanData {
            borrower: loan.borrower,
            collateral_amount: loan.collateral_amount,
            borrowed_amount: loan.borrowed_amount + interest,
            interest_rate: loan.interest_rate,
            last_update: block.height,
            is_active: loan.is_active,
        };
        
        Mapping::set(loans, loan_id, updated_loan);
    }
    
    // Withdraw collateral not tied to a loan
    transition withdraw_collateral(collateral: Collateral) -> Collateral {
        assert_eq(collateral.owner, self.signer);
        assert_eq(collateral.loan_id, 0u64); // Must not be tied to active loan
        
        return Collateral {
            owner: self.signer,
            token_type: collateral.token_type,
            amount: collateral.amount,
            loan_id: 0u64,
        };
    }
    
    // Admin: Update collateral prices
    async transition update_prices(
        public token_type: u8, 
        public price: u64
    ) -> Future {
        assert_eq(self.signer, ADMIN);
        assert(price > 0u64);
        
        let f: Future = finalize_update_prices(token_type, price);
        return f;
    }
    
    async function finalize_update_prices(token_type: u8, price: u64) {
        Mapping::set(collateral_prices, token_type, price);
    }
    
    // Admin: Update liquidation thresholds
    async transition update_threshold(
        public token_type: u8, 
        public threshold: u64
    ) -> Future {
        assert_eq(self.signer, ADMIN);
        assert(threshold > 5000u64); // At least 50%
        assert(threshold < 10000u64); // Less than 100%
        
        let f: Future = finalize_update_threshold(token_type, threshold);
        return f;
    }
    
    async function finalize_update_threshold(token_type: u8, threshold: u64) {
        Mapping::set(liquidation_threshold, token_type, threshold);
    }
    
    // Admin: Update interest rates
    async transition update_rate(
        public token_type: u8, 
        public rate: u64
    ) -> Future {
        assert_eq(self.signer, ADMIN);
        assert(rate < 5000u64); // Max 50% APR
        
        let f: Future = finalize_update_rate(token_type, rate);
        return f;
    }
    
    async function finalize_update_rate(token_type: u8, rate: u64) {
        Mapping::set(interest_rates, token_type, rate);
    }
    
    // Admin: Emergency pause
    async transition pause_system(public pause: bool) -> Future {
        assert_eq(self.signer, ADMIN);
        
        let f: Future = finalize_pause(pause);
        return f;
    }
    
    async function finalize_pause(pause: bool) {
        Mapping::set(paused, 0u8, pause);
    }
    
    // View loan health ratio
    async transition check_health(public loan_id: u64) -> Future {
        let f: Future = finalize_check_health(loan_id);
        return f;
    }
    
    async function finalize_check_health(loan_id: u64) {
        let loan: LoanData = Mapping::get(loans, loan_id);
        assert(loan.is_active);
        
        // Calculate current debt
        let blocks_elapsed: u32 = block.height - loan.last_update;
        let interest: u64 = loan.borrowed_amount * loan.interest_rate * (blocks_elapsed as u64) 
                           / (INTEREST_PRECISION * SECONDS_PER_YEAR);
        let total_debt: u64 = loan.borrowed_amount + interest;
        
        // Get price (assume token type 1)
        let price: u64 = Mapping::get_or_use(collateral_prices, 1u8, 0u64);
        let collateral_value: u64 = loan.collateral_amount * price;
        
        // Health ratio calculation (stored for reference)
        let health_ratio: u64 = collateral_value * 10000u64 / total_debt;
        
        // Just verify calculation completes
        assert(health_ratio > 0u64);
    }
}