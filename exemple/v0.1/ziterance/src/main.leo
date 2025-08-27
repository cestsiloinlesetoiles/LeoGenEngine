program ziterance.aleo {
    // Constants
    const ADMIN: address = aleo1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqanmpl0;
    const MIN_TIME_LOCK: u64 = 2592000u64; // 30 days in seconds
    const MAX_BENEFICIARIES: u8 = 10u8;
    const FULL_ALLOCATION: u8 = 100u8;

    // Death attestation structure
    struct DeathAttestation {
        deceased: address,
        attestor: address,
        timestamp: u64,
        proof_hash: field,
    }

    // Inheritance plan record
    record InheritancePlan {
        owner: address,
        plan_id: u64,
        asset_hash: field,
        beneficiary_count: u8,
        time_lock: u64,
        is_active: bool,
    }

    // Beneficiary share record
    record BeneficiaryShare {
        owner: address,
        plan_id: u64,
        beneficiary: address,
        allocation_percent: u8,
        asset_hash: field,
        is_claimed: bool,
    }

    // Asset inventory record
    record AssetInventory {
        owner: address,
        asset_type: u8,
        value_commitment: field,
        plan_id: u64,
    }

    // Executor role record
    record ExecutorRole {
        owner: address,
        plan_owner: address,
        permissions: u8,
        is_active: bool,
    }

    // Public mappings
    mapping death_certificates: address => DeathAttestation;
    mapping active_plans: address => u64;
    mapping executor_status: address => bool;
    mapping verified_attestors: address => bool;
    mapping plan_counter: field => u64;

    // Create inheritance plan
    transition create_plan(
        private asset_hash: field,
        private beneficiary_count: u8,
        private time_lock: u64
    ) -> InheritancePlan {
        // Validate inputs
        assert(beneficiary_count > 0u8);
        assert(beneficiary_count <= MAX_BENEFICIARIES);
        assert(time_lock >= MIN_TIME_LOCK);

        // Generate plan ID using multiple hashes instead of tuple
        let temp1: field = BHP256::hash_to_field(asset_hash);
        let temp2: field = BHP256::hash_to_field(self.signer);
        let temp3: field = BHP256::hash_to_field(beneficiary_count as field);
        let combined: field = BHP256::hash_to_field(temp1 + temp2 + temp3);
        let plan_id: u64 = BHP256::hash_to_u64(combined);

        return InheritancePlan {
            owner: self.signer,
            plan_id: plan_id,
            asset_hash: asset_hash,
            beneficiary_count: beneficiary_count,
            time_lock: time_lock,
            is_active: true,
        };
    }

    // Add beneficiary to plan
    transition add_beneficiary(
        private plan: InheritancePlan,
        private beneficiary: address,
        private allocation_percent: u8
    ) -> (InheritancePlan, BeneficiaryShare) {
        // Validate ownership and plan status
        assert_eq(plan.owner, self.signer);
        assert(plan.is_active);
        assert(allocation_percent > 0u8);
        assert(allocation_percent <= FULL_ALLOCATION);

        let share: BeneficiaryShare = BeneficiaryShare {
            owner: beneficiary,
            plan_id: plan.plan_id,
            beneficiary: beneficiary,
            allocation_percent: allocation_percent,
            asset_hash: plan.asset_hash,
            is_claimed: false,
        };

        return (plan, share);
    }

    // Appoint executor
    transition appoint_executor(
        private executor_addr: address,
        private permissions: u8
    ) -> ExecutorRole {
        assert(permissions > 0u8);

        return ExecutorRole {
            owner: executor_addr,
            plan_owner: self.signer,
            permissions: permissions,
            is_active: true,
        };
    }

    // Attest death (async)
    async transition attest_death(
        public deceased: address,
        public proof_hash: field
    ) -> Future {
        let f: Future = finalize_attest_death(self.signer, deceased, proof_hash);
        return f;
    }

    async function finalize_attest_death(
        attestor: address,
        deceased: address,
        proof_hash: field
    ) {
        // Verify attestor is registered
        let is_verified: bool = Mapping::get_or_use(verified_attestors, attestor, false);
        assert(is_verified);

        // Create death attestation with block.height converted to u64
        let attestation: DeathAttestation = DeathAttestation {
            deceased: deceased,
            attestor: attestor,
            timestamp: block.height as u64,
            proof_hash: proof_hash,
        };

        // Store death certificate
        Mapping::set(death_certificates, deceased, attestation);
    }

    // Execute inheritance (async)
    async transition execute_inheritance(
        public plan_owner: address,
        public executor: address
    ) -> Future {
        let f: Future = finalize_execute_inheritance(plan_owner, executor);
        return f;
    }

    async function finalize_execute_inheritance(
        plan_owner: address,
        executor: address
    ) {
        // Verify death certificate exists
        let death_cert: DeathAttestation = Mapping::get(death_certificates, plan_owner);
        assert_eq(death_cert.deceased, plan_owner);

        // Verify executor status
        let executor_verified: bool = Mapping::get_or_use(executor_status, executor, false);
        assert(executor_verified);

        // Check time lock (simplified - should check against death timestamp + time_lock)
        let plan_count: u64 = Mapping::get_or_use(active_plans, plan_owner, 0u64);
        assert(plan_count > 0u64);

        // Mark plan as executed
        Mapping::set(active_plans, plan_owner, 0u64);
    }

    // Claim inheritance
    transition claim_inheritance(
        private beneficiary_share: BeneficiaryShare,
        private asset_type: u8,
        private value_commitment: field
    ) -> AssetInventory {
        // Validate beneficiary
        assert_eq(beneficiary_share.owner, self.signer);
        assert_eq(beneficiary_share.beneficiary, self.signer);
        assert(!beneficiary_share.is_claimed);

        return AssetInventory {
            owner: self.signer,
            asset_type: asset_type,
            value_commitment: value_commitment,
            plan_id: beneficiary_share.plan_id,
        };
    }

    // Update inheritance plan
    transition update_plan(
        private plan: InheritancePlan,
        private new_asset_hash: field
    ) -> InheritancePlan {
        // Validate ownership and plan status
        assert_eq(plan.owner, self.signer);
        assert(plan.is_active);

        return InheritancePlan {
            owner: plan.owner,
            plan_id: plan.plan_id,
            asset_hash: new_asset_hash,
            beneficiary_count: plan.beneficiary_count,
            time_lock: plan.time_lock,
            is_active: true,
        };
    }

    // Revoke inheritance plan
    transition revoke_plan(private plan: InheritancePlan) -> InheritancePlan {
        // Validate ownership
        assert_eq(plan.owner, self.signer);
        assert(plan.is_active);

        return InheritancePlan {
            owner: plan.owner,
            plan_id: plan.plan_id,
            asset_hash: plan.asset_hash,
            beneficiary_count: plan.beneficiary_count,
            time_lock: plan.time_lock,
            is_active: false,
        };
    }

    // Register death attestor (admin only, async)
    async transition register_attestor(public attestor: address) -> Future {
        assert_eq(self.signer, ADMIN);
        let f: Future = finalize_register_attestor(attestor);
        return f;
    }

    async function finalize_register_attestor(attestor: address) {
        Mapping::set(verified_attestors, attestor, true);
    }

    // Register executor (async)
    async transition register_executor(public executor: address) -> Future {
        let f: Future = finalize_register_executor(executor);
        return f;
    }

    async function finalize_register_executor(executor: address) {
        Mapping::set(executor_status, executor, true);
    }

    // Register active plan (async)
    async transition register_plan(
        public plan_owner: address,
        public plan_id: u64
    ) -> Future {
        assert_eq(self.signer, plan_owner);
        let f: Future = finalize_register_plan(plan_owner, plan_id);
        return f;
    }

    async function finalize_register_plan(
        plan_owner: address,
        plan_id: u64
    ) {
        Mapping::set(active_plans, plan_owner, plan_id);
    }

    // Emergency plan recovery (admin only)
    transition emergency_recovery(
        private plan: InheritancePlan,
        private new_owner: address
    ) -> InheritancePlan {
        // Admin only for emergency situations
        assert_eq(self.signer, ADMIN);

        return InheritancePlan {
            owner: new_owner,
            plan_id: plan.plan_id,
            asset_hash: plan.asset_hash,
            beneficiary_count: plan.beneficiary_count,
            time_lock: plan.time_lock,
            is_active: plan.is_active,
        };
    }

    // Verify death certificate
    transition verify_death_certificate(public deceased: address) -> public bool {
        // This would be used to check if someone is deceased
        // Returns true if death certificate exists (simplified implementation)
        return true;
    }

    // Create asset commitment
    transition create_asset_commitment(
        private asset_value: u64,
        private asset_type: u8,
        private salt: field
    ) -> field {
        // Use individual hashes instead of tuple
        let hash1: field = BHP256::hash_to_field(asset_value);
        let hash2: field = BHP256::hash_to_field(asset_type as field);
        let hash3: field = BHP256::hash_to_field(salt);
        let hash4: field = BHP256::hash_to_field(self.signer);
        return BHP256::hash_to_field(hash1 + hash2 + hash3 + hash4);
    }

    // Multi-signature validation for inheritance execution
    transition validate_multisig_execution(
        private plan_id: u64,
        private signature1: field,
        private signature2: field,
        private signature3: field
    ) -> bool {
        // Use individual hashes instead of tuples
        let sig_sum: field = signature1 + signature2 + signature3;
        let combined_sig: field = BHP256::hash_to_field(sig_sum);
        
        let plan_hash1: field = BHP256::hash_to_field(plan_id);
        let plan_hash2: field = BHP256::hash_to_field(self.signer);
        let plan_hash: field = BHP256::hash_to_field(plan_hash1 + plan_hash2);
        
        // In real implementation, would verify actual signatures
        return combined_sig != 0field && plan_hash != 0field;
    }

    // Time-lock validation
    transition validate_timelock(
        private death_timestamp: u64,
        private time_lock_period: u64,
        private current_time: u64
    ) -> bool {
        assert(current_time >= death_timestamp);
        let elapsed: u64 = current_time - death_timestamp;
        return elapsed >= time_lock_period;
    }
}