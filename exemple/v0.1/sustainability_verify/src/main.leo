program sustainability_verify.aleo {
    // Constants
    const ADMIN: address = aleo1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqanmpl0;
    const MAX_REPUTATION: u64 = 1000u64;
    const MIN_VERIFICATION_TIME: u64 = 86400u64; // 1 day in seconds
    const PROOF_THRESHOLD: u64 = 80u64; // 80% achievement threshold

    // Data Structures
    struct CommitmentInfo {
        organization: address,
        commitment_hash: field,
        target_date: u64,
        category: u8, // 1=Carbon, 2=Water, 3=Waste, 4=Energy
        timestamp: u64
    }

    struct VerificationProof {
        commitment_hash: field,
        achievement_score: u64,
        proof_data: field,
        verifier: address,
        timestamp: u64
    }

    struct ReputationData {
        total_commitments: u64,
        verified_commitments: u64,
        reputation_score: u64,
        last_updated: u64
    }

    // Private Records
    record Commitment {
        owner: address,
        organization: address,
        sustainability_target: field, // Encrypted target data
        target_amount: u64,
        category: u8,
        deadline: u64,
        secret_key: field,
        commitment_hash: field
    }

    record ProofSubmission {
        owner: address,
        commitment_hash: field,
        actual_achievement: u64,
        proof_data: field,
        verification_key: field,
        timestamp: u64
    }

    record AdminToken {
        owner: address,
        admin_level: u8,
        issued_at: u64
    }

    // Public Mappings
    mapping org_commitments: address => field; // organization -> latest commitment hash
    mapping commitment_status: field => u8; // hash -> status (0=pending, 1=verified, 2=failed)
    mapping org_reputation: address => ReputationData;
    mapping commitment_info: field => CommitmentInfo;
    mapping verification_proofs: field => VerificationProof;
    mapping verification_standards: u8 => u64; // category -> minimum threshold
    mapping trusted_verifiers: address => bool;

    // Private commitment creation
    transition create_commitment(
        organization: address,
        target_amount: u64,
        category: u8,
        deadline: u64,
        secret_data: field
    ) -> Commitment {
        // Input validation
        assert(target_amount > 0u64);
        assert(category >= 1u8 && category <= 4u8);
        assert(deadline > 0u64);
        
        // Generate commitment hash from secret data and target
        let commitment_hash: field = BHP256::hash_to_field(organization);
        let commitment_hash2: field = BHP256::hash_to_field(target_amount);
        let commitment_hash3: field = BHP256::hash_to_field(category);
        let commitment_hash4: field = BHP256::hash_to_field(deadline);
        let commitment_hash5: field = BHP256::hash_to_field(secret_data);
        let commitment_hash_final: field = BHP256::hash_to_field(commitment_hash + commitment_hash2 + commitment_hash3 + commitment_hash4 + commitment_hash5);
        
        // Create encrypted target data - convert target_amount to field first
        let target_amount_field: field = target_amount as field;
        let sustainability_target: field = BHP256::hash_to_field(target_amount_field + secret_data);
        
        // Generate secret key for proof verification
        let secret_key: field = BHP256::hash_to_field(secret_data + BHP256::hash_to_field(self.signer));
        
        return Commitment {
            owner: self.signer,
            organization: organization,
            sustainability_target: sustainability_target,
            target_amount: target_amount,
            category: category,
            deadline: deadline,
            secret_key: secret_key,
            commitment_hash: commitment_hash_final
        };
    }

    // Register commitment publicly
    async transition register_commitment(
        commitment: Commitment,
        public target_date: u64
    ) -> (Commitment, Future) {
        assert_eq(commitment.owner, self.signer);
        assert(target_date >= commitment.deadline);
        
        let f: Future = finalize_register(
            commitment.organization,
            commitment.commitment_hash,
            target_date,
            commitment.category
        );
        
        return (commitment, f);
    }

    async function finalize_register(
        organization: address,
        commitment_hash: field,
        target_date: u64,
        category: u8
    ) {
        // Update organization's latest commitment
        Mapping::set(org_commitments, organization, commitment_hash);
        
        // Set commitment status to pending
        Mapping::set(commitment_status, commitment_hash, 0u8);
        
        // Store commitment information
        let info: CommitmentInfo = CommitmentInfo {
            organization: organization,
            commitment_hash: commitment_hash,
            target_date: target_date,
            category: category,
            timestamp: block.height as u64
        };
        Mapping::set(commitment_info, commitment_hash, info);
        
        // Initialize or update reputation data
        let current_rep: ReputationData = Mapping::get_or_use(
            org_reputation, 
            organization, 
            ReputationData {
                total_commitments: 0u64,
                verified_commitments: 0u64,
                reputation_score: 0u64,
                last_updated: 0u64
            }
        );
        
        let updated_rep: ReputationData = ReputationData {
            total_commitments: current_rep.total_commitments + 1u64,
            verified_commitments: current_rep.verified_commitments,
            reputation_score: current_rep.reputation_score,
            last_updated: block.height as u64
        };
        
        Mapping::set(org_reputation, organization, updated_rep);
    }

    // Submit private proof
    transition submit_proof(
        commitment: Commitment,
        actual_achievement: u64,
        proof_data: field
    ) -> ProofSubmission {
        assert_eq(commitment.owner, self.signer);
        assert(actual_achievement > 0u64);
        
        // Verify proof corresponds to commitment
        let achievement_field: field = actual_achievement as field;
        let verification_key: field = BHP256::hash_to_field(commitment.commitment_hash + BHP256::hash_to_field(achievement_field) + proof_data);
        
        return ProofSubmission {
            owner: self.signer,
            commitment_hash: commitment.commitment_hash,
            actual_achievement: actual_achievement,
            proof_data: proof_data,
            verification_key: verification_key,
            timestamp: 0u64 // Set in finalize
        };
    }

    // Verify achievement publicly
    async transition verify_achievement(
        proof: ProofSubmission,
        public verifier_sig: field
    ) -> (ProofSubmission, Future) {
        assert_eq(proof.owner, self.signer);
        
        let f: Future = finalize_verify(
            proof.commitment_hash,
            proof.actual_achievement,
            proof.proof_data,
            self.signer,
            verifier_sig
        );
        
        return (proof, f);
    }

    async function finalize_verify(
        commitment_hash: field,
        achievement: u64,
        proof_data: field,
        verifier: address,
        sig: field
    ) {
        // Check if verifier is trusted
        let is_trusted: bool = Mapping::get_or_use(trusted_verifiers, verifier, false);
        assert(is_trusted);
        
        // Get commitment info
        let info: CommitmentInfo = Mapping::get(commitment_info, commitment_hash);
        
        // Check if verification is within allowed timeframe
        assert(block.height as u64 >= info.target_date);
        assert(block.height as u64 <= info.target_date + MIN_VERIFICATION_TIME);
        
        // Get verification threshold for category
        let threshold: u64 = Mapping::get_or_use(verification_standards, info.category, PROOF_THRESHOLD);
        
        // Calculate achievement percentage (assuming 100 is 100%)
        let is_verified: bool = achievement >= threshold;
        let status: u8 = is_verified ? 1u8 : 2u8;
        
        // Update commitment status
        Mapping::set(commitment_status, commitment_hash, status);
        
        // Store verification proof
        let verification: VerificationProof = VerificationProof {
            commitment_hash: commitment_hash,
            achievement_score: achievement,
            proof_data: proof_data,
            verifier: verifier,
            timestamp: block.height as u64
        };
        Mapping::set(verification_proofs, commitment_hash, verification);
        
        // Update organization reputation
        let current_rep: ReputationData = Mapping::get(org_reputation, info.organization);
        
        let new_verified: u64 = is_verified ? 
            current_rep.verified_commitments + 1u64 : 
            current_rep.verified_commitments;
        
        // Calculate new reputation score (percentage * 10 for precision)
        let reputation_score: u64 = current_rep.total_commitments > 0u64 ?
            (new_verified * MAX_REPUTATION) / current_rep.total_commitments :
            0u64;
        
        let updated_rep: ReputationData = ReputationData {
            total_commitments: current_rep.total_commitments,
            verified_commitments: new_verified,
            reputation_score: reputation_score,
            last_updated: block.height as u64
        };
        
        Mapping::set(org_reputation, info.organization, updated_rep);
    }

    // Get reputation score (public query)
    async transition get_reputation(public organization: address) -> Future {
        let f: Future = finalize_get_reputation(organization);
        return f;
    }

    async function finalize_get_reputation(organization: address) {
        let rep_data: ReputationData = Mapping::get_or_use(
            org_reputation,
            organization,
            ReputationData {
                total_commitments: 0u64,
                verified_commitments: 0u64,
                reputation_score: 0u64,
                last_updated: 0u64
            }
        );
        
        // Reputation data is now publicly visible through the mapping
        assert(rep_data.reputation_score <= MAX_REPUTATION);
    }

    // Admin Functions
    transition create_admin_token() -> AdminToken {
        assert_eq(self.signer, ADMIN);
        
        return AdminToken {
            owner: self.signer,
            admin_level: 1u8,
            issued_at: 0u64 // Set in finalize if needed
        };
    }

    // Set verification standards
    async transition set_verification_standard(
        admin_token: AdminToken,
        public category: u8,
        public threshold: u64
    ) -> (AdminToken, Future) {
        assert_eq(admin_token.owner, self.signer);
        assert_eq(self.signer, ADMIN);
        assert(category >= 1u8 && category <= 4u8);
        assert(threshold <= 100u64);
        
        let f: Future = finalize_set_standard(category, threshold);
        return (admin_token, f);
    }

    async function finalize_set_standard(category: u8, threshold: u64) {
        Mapping::set(verification_standards, category, threshold);
    }

    // Manage trusted verifiers
    async transition add_trusted_verifier(
        admin_token: AdminToken,
        public verifier: address
    ) -> (AdminToken, Future) {
        assert_eq(admin_token.owner, self.signer);
        assert_eq(self.signer, ADMIN);
        
        let f: Future = finalize_add_verifier(verifier);
        return (admin_token, f);
    }

    async function finalize_add_verifier(verifier: address) {
        Mapping::set(trusted_verifiers, verifier, true);
    }

    // Remove trusted verifier
    async transition remove_trusted_verifier(
        admin_token: AdminToken,
        public verifier: address
    ) -> (AdminToken, Future) {
        assert_eq(admin_token.owner, self.signer);
        assert_eq(self.signer, ADMIN);
        
        let f: Future = finalize_remove_verifier(verifier);
        return (admin_token, f);
    }

    async function finalize_remove_verifier(verifier: address) {
        Mapping::set(trusted_verifiers, verifier, false);
    }

    // Query commitment status (public)
    async transition query_commitment_status(public commitment_hash: field) -> Future {
        let f: Future = finalize_query_status(commitment_hash);
        return f;
    }

    async function finalize_query_status(commitment_hash: field) {
        let status: u8 = Mapping::get_or_use(commitment_status, commitment_hash, 0u8);
        assert(status <= 2u8); // Ensure valid status
    }

    // Audit function for admin
    async transition audit_organization(
        admin_token: AdminToken,
        public organization: address
    ) -> (AdminToken, Future) {
        assert_eq(admin_token.owner, self.signer);
        assert_eq(self.signer, ADMIN);
        
        let f: Future = finalize_audit(organization);
        return (admin_token, f);
    }

    async function finalize_audit(organization: address) {
        // Get organization's reputation data for audit
        let rep_data: ReputationData = Mapping::get_or_use(
            org_reputation,
            organization,
            ReputationData {
                total_commitments: 0u64,
                verified_commitments: 0u64,
                reputation_score: 0u64,
                last_updated: 0u64
            }
        );
        
        // Get latest commitment hash
        let latest_commitment: field = Mapping::get_or_use(org_commitments, organization, 0field);
        
        // Audit data is now accessible through mappings
        assert(rep_data.verified_commitments <= rep_data.total_commitments);
    }
}