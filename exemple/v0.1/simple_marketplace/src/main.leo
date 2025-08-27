program simple_marketplace.aleo {
    // Constants
    const ADMIN: address = aleo1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqanmpl0;
    const MARKETPLACE_FEE_PCT: u64 = 5u64; // 5% fee
    const MAX_REPUTATION: u64 = 1000u64;
    const MAX_TITLE_LEN: u8 = 32u8;

    // Structs
    struct ItemListing {
        id: field,
        seller: address,
        price: u64,
        title: field,
        available: bool,
    }

    // Records
    record Token {
        owner: address,
        amount: u64,
    }

    record Receipt {
        owner: address,
        item_id: field,
        seller: address,
        price_paid: u64,
        timestamp: u64,
    }

    // Public mappings
    mapping listings: field => ItemListing;
    mapping reputation: address => u64;
    mapping availability: field => bool;
    mapping marketplace_revenue: field => u64;

    // Private token transfer
    transition private_transfer(token: Token, to: address, amount: u64) -> (Token, Token) {
        // Verify sender owns the token
        assert_eq(token.owner, self.signer);
        assert(amount > 0u64);
        assert(amount <= token.amount);

        // Create tokens for recipient and sender
        let recipient_token: Token = Token {
            owner: to,
            amount: amount,
        };

        let sender_token: Token = Token {
            owner: self.signer,
            amount: token.amount - amount,
        };

        return (recipient_token, sender_token);
    }

    // List an item for sale
    async transition list_item(
        public item_id: field,
        public price: u64,
        public title: field
    ) -> Future {
        assert(price > 0u64);
        assert(price <= 1000000u64); // Max price limit
        
        let f: Future = finalize_list_item(item_id, self.signer, price, title);
        return f;
    }

    async function finalize_list_item(
        item_id: field,
        seller: address,
        price: u64,
        title: field
    ) {
        // Ensure item doesn't already exist
        assert(!Mapping::contains(listings, item_id));

        // Create listing
        let listing: ItemListing = ItemListing {
            id: item_id,
            seller: seller,
            price: price,
            title: title,
            available: true,
        };

        // Store listing and mark as available
        Mapping::set(listings, item_id, listing);
        Mapping::set(availability, item_id, true);

        // Initialize seller reputation if first listing
        if (!Mapping::contains(reputation, seller)) {
            Mapping::set(reputation, seller, 100u64);
        }
    }

    // Purchase an item with private payment
    async transition purchase_item(
        token: Token,
        public item_id: field
    ) -> (Token, Receipt, Future) {
        // Get current timestamp hash for receipt
        let timestamp_hash: field = BHP256::hash_to_field(item_id);
        
        let f: Future = finalize_purchase_item(token.amount, item_id, self.signer);
        
        // Create receipt for buyer
        let receipt: Receipt = Receipt {
            owner: self.signer,
            item_id: item_id,
            seller: aleo1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqanmpl0, // Placeholder
            price_paid: token.amount,
            timestamp: 0u64,
        };

        // Return remaining token (if any) to buyer
        let remaining_token: Token = Token {
            owner: self.signer,
            amount: 0u64, // Will be calculated in finalize
        };

        return (remaining_token, receipt, f);
    }

    async function finalize_purchase_item(
        payment_amount: u64,
        item_id: field,
        buyer: address
    ) {
        // Get listing and verify availability
        let listing: ItemListing = Mapping::get(listings, item_id);
        assert(listing.available);
        assert(Mapping::get(availability, item_id));

        // Verify sufficient payment
        assert(payment_amount >= listing.price);

        // Calculate marketplace fee
        let fee: u64 = listing.price * MARKETPLACE_FEE_PCT / 100u64;
        let seller_payment: u64 = listing.price - fee;

        // Mark item as sold
        let sold_listing: ItemListing = ItemListing {
            id: listing.id,
            seller: listing.seller,
            price: listing.price,
            title: listing.title,
            available: false,
        };

        Mapping::set(listings, item_id, sold_listing);
        Mapping::set(availability, item_id, false);

        // Update marketplace revenue
        let current_revenue: u64 = Mapping::get_or_use(marketplace_revenue, 0field, 0u64);
        Mapping::set(marketplace_revenue, 0field, current_revenue + fee);

        // Update seller reputation (positive for successful sale)
        let seller_rep: u64 = Mapping::get_or_use(reputation, listing.seller, 100u64);
        let new_rep: u64 = seller_rep < MAX_REPUTATION - 10u64 ? seller_rep + 10u64 : MAX_REPUTATION;
        Mapping::set(reputation, listing.seller, new_rep);
    }

    // Remove a listing (seller only)
    async transition remove_listing(public item_id: field) -> Future {
        let f: Future = finalize_remove_listing(item_id, self.signer);
        return f;
    }

    async function finalize_remove_listing(item_id: field, caller: address) {
        // Get listing and verify ownership
        let listing: ItemListing = Mapping::get(listings, item_id);
        assert_eq(listing.seller, caller);
        assert(listing.available);

        // Mark as unavailable
        let removed_listing: ItemListing = ItemListing {
            id: listing.id,
            seller: listing.seller,
            price: listing.price,
            title: listing.title,
            available: false,
        };

        Mapping::set(listings, item_id, removed_listing);
        Mapping::set(availability, item_id, false);
    }

    // Update item price (seller only)
    async transition update_price(public item_id: field, public new_price: u64) -> Future {
        assert(new_price > 0u64);
        assert(new_price <= 1000000u64);
        
        let f: Future = finalize_update_price(item_id, new_price, self.signer);
        return f;
    }

    async function finalize_update_price(item_id: field, new_price: u64, caller: address) {
        // Get listing and verify ownership
        let listing: ItemListing = Mapping::get(listings, item_id);
        assert_eq(listing.seller, caller);
        assert(listing.available);

        // Update price
        let updated_listing: ItemListing = ItemListing {
            id: listing.id,
            seller: listing.seller,
            price: new_price,
            title: listing.title,
            available: listing.available,
        };

        Mapping::set(listings, item_id, updated_listing);
    }

    // Admin: Emergency remove listing
    async transition admin_remove_listing(public item_id: field) -> Future {
        assert_eq(self.signer, ADMIN);
        
        let f: Future = finalize_admin_remove(item_id);
        return f;
    }

    async function finalize_admin_remove(item_id: field) {
        let listing: ItemListing = Mapping::get(listings, item_id);
        
        let removed_listing: ItemListing = ItemListing {
            id: listing.id,
            seller: listing.seller,
            price: listing.price,
            title: listing.title,
            available: false,
        };

        Mapping::set(listings, item_id, removed_listing);
        Mapping::set(availability, item_id, false);

        // Penalize seller reputation
        let seller_rep: u64 = Mapping::get_or_use(reputation, listing.seller, 100u64);
        let new_rep: u64 = seller_rep >= 50u64 ? seller_rep - 50u64 : 0u64;
        Mapping::set(reputation, listing.seller, new_rep);
    }

    // Admin: Collect marketplace fees
    async transition collect_fees(public amount: u64) -> (Token, Future) {
        assert_eq(self.signer, ADMIN);
        
        let fee_token: Token = Token {
            owner: ADMIN,
            amount: amount,
        };

        let f: Future = finalize_collect_fees(amount);
        return (fee_token, f);
    }

    async function finalize_collect_fees(amount: u64) {
        let current_revenue: u64 = Mapping::get_or_use(marketplace_revenue, 0field, 0u64);
        assert(current_revenue >= amount);
        Mapping::set(marketplace_revenue, 0field, current_revenue - amount);
    }

    // Admin: Adjust reputation (for dispute resolution)
    async transition adjust_reputation(
        public seller: address,
        public adjustment: i64
    ) -> Future {
        assert_eq(self.signer, ADMIN);
        
        let f: Future = finalize_adjust_reputation(seller, adjustment);
        return f;
    }

    async function finalize_adjust_reputation(seller: address, adjustment: i64) {
        let current_rep: u64 = Mapping::get_or_use(reputation, seller, 100u64);
        
        // Use ternary operator to avoid re-assignment error
        let new_rep: u64 = adjustment >= 0i64 
            ? (current_rep + (adjustment as u64) < MAX_REPUTATION 
                ? current_rep + (adjustment as u64) 
                : MAX_REPUTATION)
            : (current_rep >= ((-adjustment) as u64) 
                ? current_rep - ((-adjustment) as u64) 
                : 0u64);

        Mapping::set(reputation, seller, new_rep);
    }

    // Mint tokens (for demo purposes)
    transition mint_tokens(public amount: u64) -> Token {
        assert(amount > 0u64);
        assert(amount <= 10000u64); // Max mint per transaction
        
        return Token {
            owner: self.signer,
            amount: amount,
        };
    }
}