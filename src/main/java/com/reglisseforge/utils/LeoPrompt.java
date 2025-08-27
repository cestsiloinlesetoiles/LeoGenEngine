package com.reglisseforge.utils;

import java.util.List;

import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaTextBlockParam;

/**
 * Leo prompt management system for code generation.
 * Provides Leo language rules and generation prompts (clarified to avoid async/finalize ambiguities).
 */
public final class LeoPrompt {

    private LeoPrompt() {}

         // Admin placeholder - replace with actual address in generated code
     public static final String ADMIN_PLACEHOLDER = "aleo1ufj2q79zyspekyrrtukmx9u8ruyjrcvm6pvsxjk42m48ejepcgysucum7x"; // Valid address for testing

    /**
     * Returns a comprehensive Leo language rules reference.
     * Based on zLearn Chapter 3 and extended with complete syntax, operators, and patterns.
     */
    public static String LeoRulesBook() {
        return """
        LEO LANGUAGE COMPREHENSIVE REFERENCE

        CRITICAL: Since Leo v2.0.0, 'finalize' is DEPRECATED! Use 'async function' instead!

        PROGRAM STRUCTURE (STRICT ORDER)
        program name.aleo {
            // 1. Constants
            const MAX_SUPPLY: u64 = 1000000u64;
            const ADMIN: address = aleo1ufj2q79zyspekyrrtukmx9u8ruyjrcvm6pvsxjk42m48ejepcgysucum7x; // Valid testing address
            
            // 2. Structs (value types)
            struct Data { field1: type, field2: type }
            
            // 3. Records (private state - MUST have owner first)
            record Token { owner: address, amount: u64 }
            
            // 4. Mappings (public state)
            mapping balances: address => u64;
            
            // 5. Transitions (regular and async)
            transition transfer(...) -> ... { }
            async transition mint(...) -> Future { }
            
            // 6. Async functions (for on-chain execution)
            async function update_balances(...) { }
        }

        TYPES & LITERALS
        • Integers: u8, u16, u32, u64, u128, i8, i16, i32, i64, i128
        • Field elements: field (prime field element)
        • Scalars: scalar (scalar field element)
        • Groups: group (group element)
        • Addresses: address (Aleo address)
        • Booleans: bool (true/false)
        • Arrays: [type; N] (fixed-size only, N must be constant)
        • Tuples: (T1, T2, ..., Tn)
        • Structs: struct Name { field: type, ... }
        • Records: record Name { owner: address, ... }
        • NOT SUPPORTED: strings (use field/u128 for identifiers if needed)

        LITERAL SUFFIXES (MANDATORY):
        • Unsigned: 42u8, 100u64, 1000000u128
        • Signed: -5i8, 42i32, -1000i64
        • Field: 1field, 42field
        • Scalar: 1scalar
        • Group: 1group, 2group
        • Boolean: true, false (no suffix)
        • Address: aleo1... (no quotes, must be 63 chars, only lowercase letters and numbers)
        • IMPORTANT: Leo does NOT support string literals!
        • IMPORTANT: Valid address format: aleo1[58 lowercase letters/numbers] (total 63 chars)

        VISIBILITY (PUBLIC VS PRIVATE)
        • DEFAULT: private (if not specified)
        • Transition inputs: transition foo(private a: u64, public b: u64)
        • Transition outputs: -> private u64 or -> public u64
        • Records: ALWAYS private (encrypted to owner)
        • Mappings: ALWAYS public (on-chain readable)
        • Async function params: ALWAYS public
        • Context: self.signer (private), self.caller, block.height (async only)

        RECORDS RULES
        • MUST have owner: address as FIRST field
        • Are consumed when used (UTXO model)
        • Only owner can consume
        • Encrypted with owner's address
        • Cannot be updated in-place
        • Programs cannot own records (only users)
        • Auto-generated _nonce field

        MAPPINGS (PUBLIC STATE)
        • Declaration: mapping name: key_type => value_type;
        • CRITICAL: Leo does NOT support strings! Use mapping names directly, not as strings
        • Operations (ONLY in async functions):
          - Mapping::get(mapping_name, key) -> value (panics if not found)
          - Mapping::get_or_use(mapping_name, key, default) -> value
          - Mapping::set(mapping_name, key, value)
          - Mapping::remove(mapping_name, key)
          - Mapping::contains(mapping_name, key) -> bool
        • Example: Mapping::set(balances, owner, 100u64); // NOT "balances"!
        
        TRANSITIONS
        Regular (no mappings):
        transition name(params) -> output {
            // Cannot call async functions
            // Cannot return Future
            // Cannot access mappings
        }

        Async (with mappings):
        async transition name(params) -> Future {
            // Can return only Future
            return async_function_name(args);
        }

        async transition name(params) -> (Record, Future) {
            // Can return (Record, Future)
            let rec: Record = Record { ... };
            return (rec, async_function_name(args));
        }

        ASYNC FUNCTIONS (replaces deprecated 'finalize')
        async function name(public param1: type1, public param2: type2) {
            // NO return statement
            // ALL parameters MUST be public
            // Can access mappings
            // Can access block.height
            // Executes on-chain after proof verification
        }

        OPERATORS (PRECEDENCE HIGH TO LOW)
        1. Unary: !, - (negation)
        2. Exponentiation: ** (use .pow() for wrapped)
        3. Multiplicative: *, /, %
        4. Additive: +, -
        5. Shift: <<, >>
        6. Bitwise AND: &
        7. Bitwise XOR: ^
        8. Bitwise OR: |
        9. Relational: <, >, <=, >=
        10. Equality: ==, !=
        11. Logical AND: &&
        12. Logical OR: ||
        13. Ternary: condition ? true_val : false_val
        14. Assignment: =, +=, -=, *=, /=, **=, %=

        ARITHMETIC OPERATIONS
        Checked (default - panics on overflow):
        • +, -, *, /, **, % 

        Wrapped (wraps on overflow):
        • add_wrapped(), sub_wrapped(), mul_wrapped()
        • div_wrapped(), pow_wrapped(), rem_wrapped()

        Other methods:
        • abs(), abs_wrapped()
        • double(), square()
        • square_root() (field only)
        • inv() (field only)

        CONTROL FLOW
        If/Else:
        if condition {
            // code
        } else if other_condition {
            // code
        } else {
            // code
        }

        Assertions:
        • assert(condition);
        • assert_eq(a, b);
        • assert_neq(a, b);

        For loops (bounds MUST be constants):
        for i: u32 in 0u32..10u32 {
            // code
        }

        NO: while loops, recursion, dynamic bounds

        HASH FUNCTIONS
        • BHP256/512/768/1024::hash_to_field/scalar/address/group()
        • Pedersen64/128::hash_to_field/scalar/address/group()
        • Poseidon2/4/8::hash_to_field/scalar/address/group()
        • Keccak256/384/512::hash_to_field/scalar/address/group()
        • SHA3_256/384/512::hash_to_field/scalar/address/group()

        RANDOM (ASYNC ONLY)
        In async functions only:
        • ChaCha::rand_bool()
        • ChaCha::rand_u8/u16/u32/u64/u128()
        • ChaCha::rand_i8/i16/i32/i64/i128()
        • ChaCha::rand_field/scalar/group/address()

        TYPE CASTING
        • Syntax: value as new_type
        • Examples: 42u32 as u64, 0group as address
        • Cannot cast negative to unsigned
        • Cannot cast if overflow

        SIGNATURE VERIFICATION
        signature::verify(sig, signer, message) -> bool

        CONTEXT VARIABLES
        • self.signer: address (transaction signer, private)
        • self.caller: address (calling program/account)
        • block.height: u32 (current height, async only)

        IDENTIFIER LIMITS & RESERVED WORDS
        • Maximum length: 31 bytes
        • Use short names: update_balance ✓, add_supply ✓
        • Avoid: async_transfer_private_to_public_with_fee ✗ (too long)
        • RESERVED WORDS (cannot use as identifiers): address, bool, field, group, scalar, 
          u8, u16, u32, u64, u128, i8, i16, i32, i64, i128, 
          true, false, transition, async, function, let, const, return, if, else, for, in,
          assert, assert_eq, assert_neq, struct, record, mapping, public, private

        COMMON ERRORS TO AVOID
        • EPAR0370001: Invalid address literal (must be aleo1 + 58 lowercase alphanumeric)
        • EPAR0370009: Reserved word used as identifier (e.g., 'address' as parameter name)
        • ETYC0372019: Missing owner field in record
        • ETYC0372045: Strings are not yet supported (use identifiers directly)
        • ETYC0372117: Expected a mapping but type string was found (don't use quotes!)
        • ETYC0372067: Mapping operation outside async function
        • ETYC0372034: block.height outside async function
        • ETYC0372009: Using ChainInfo instead of block.height
        • ETYC0372106: Return in async function
        • ETYC0372057: Record in function (not transition)
        • ETYC0372110: Future returned from non-async transition
        • ETYC0372101: Async function called from regular transition
        • ETYC0372120: Type mismatch in operations
        • EPAR0370005: Async function inside transition block
        """;
    }

    /**
     * Generates a user prompt for Leo code generation with strict, unambiguous rules.
     *
     * @param projectName  The Leo project name (lowercase_underscore format)
     * @param description  Project description and requirements
     * @param adminAddress Admin address (null uses default)
     * @return TextBlockParam containing the formatted prompt for Leo code generation
     */
    public static BetaTextBlock LeoGenPrompt(String projectName, String description, String adminAddress) {
        if (projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("projectName is required and must be non-blank");
        }

        final String admin = (adminAddress != null && !adminAddress.isBlank())
                ? adminAddress
                : ADMIN_PLACEHOLDER;

        final String prompt = """
        Generate complete, compilable Leo code for the following project.

        PROJECT NAME: %s
        DESCRIPTION: %s

        CRITICAL: Since Leo v2.0.0, 'finalize' is DEPRECATED! Use 'async function' instead!

        IMPORTANT: Admin address "%s" is a PLACEHOLDER. In production:
        • Replace with a real Aleo address (63 chars starting with aleo1...)
        • Or remove admin functionality if not needed
        • Valid example: aleo1qnr4dkkvkgfqph0vzc3y6z2eu975wnpz2925ntjccd5cfqxtyu8sta57j8

        PROGRAM STRUCTURE (STRICT ORDER)
        program %s.aleo {
            // 1. Constants
            const ADMIN: address = %s; // PLACEHOLDER - must be replaced!
            const MAX_SUPPLY: u64 = 1000000u64;
            
            // 2. Structs (if needed)
            struct Data { field1: type, field2: type }
            
            // 3. Records (owner MUST be first)
            record Token { owner: address, amount: u64 }
            
            // 4. Mappings (declare with names, not strings)
            mapping balances: address => u64;
            mapping total_supply: field => u64;  // Use field as key if needed
            
            // 5. Transitions
            transition regular_tx(...) -> ... { }
            async transition async_tx(...) -> Future { }
            
            // 6. Async functions (for on-chain execution)
            async function update_state(...) { }
        }

        CRITICAL RULES
        • Records: owner: address MUST be first field
        • Literals: ALL need suffixes (42u64, 1field, true)
        • NO STRINGS: Leo does NOT support string literals!
        • Mappings: Use identifiers directly, NOT quoted strings
          - CORRECT: Mapping::set(balances, owner, 100u64);
          - WRONG: Mapping::set("balances", owner, 100u64);
        • Parameter names: NEVER use reserved words (address, bool, field, etc.)
          - CORRECT: add_balance(public recipient: address, public amount: u64)
          - WRONG: add_balance(public address: address, public amount: u64)
        • Address format: Must be exactly aleo1 + 58 lowercase letters/numbers
        • Async: transitions modifying mappings MUST be async
        • Async functions: NO return statement, ALL params public
        • Mappings: operations ONLY in async functions with Mapping::
        • Identifiers: ≤ 31 bytes (use short names)
        • NO "then" keyword exists in Leo

        ASYNC FUNCTION PATTERNS
        
        Pattern 1: Async returning only Future
        ```leo
        async transition transfer_public(public to: address, public amount: u64) -> Future {
            assert(amount > 0u64);
            return transfer_public(self.signer, to, amount);
        }
        
        async function transfer_public(public from: address, public to: address, public amount: u64) {
            let from_balance: u64 = Mapping::get_or_use(balances, from, 0u64);
            assert(from_balance >= amount);
            Mapping::set(balances, from, from_balance - amount);
            
            let to_balance: u64 = Mapping::get_or_use(balances, to, 0u64);
            assert(to_balance + amount >= to_balance); // overflow check
            Mapping::set(balances, to, to_balance + amount);
        }
        ```
        
        Pattern 2: Async returning (Record, Future)
        ```leo
        async transition mint(receiver: address, amount: u64) -> (Token, Future) {
            assert_eq(self.signer, ADMIN);
            assert(amount > 0u64);
            
            let token: Token = Token {
                owner: receiver,
                amount: amount
            };
            
            return (token, update_supply(amount));
        }
        
        async function update_supply(public amount: u64) {
            let supply: u64 = Mapping::get_or_use(total_supply, 0field, 0u64);
            assert(supply + amount >= supply); // overflow check
            assert(supply + amount <= MAX_SUPPLY);
            Mapping::set(total_supply, 0field, supply + amount);
        }
        ```

        PRIVACY PATTERNS
        
        Private-to-Private Transfer:
        ```leo
        transition transfer_private(sender_token: Token, receiver: address, amount: u64) -> (Token, Token) {
            assert(sender_token.amount >= amount);
            
            let remaining: Token = Token {
                owner: sender_token.owner,
                amount: sender_token.amount - amount
            };
            
            let payment: Token = Token {
                owner: receiver,
                amount: amount
            };
            
            return (remaining, payment);
        }
        ```
        
        Private-to-Public:
        ```leo
        async transition deposit(token: Token) -> Future {
            assert_eq(token.owner, self.signer);
            return add_balance(self.signer, token.amount);
        }
        
        async function add_balance(public owner: address, public amount: u64) {
            let balance: u64 = Mapping::get_or_use(balances, owner, 0u64);
            Mapping::set(balances, owner, balance + amount);
        }
        ```

        CONTROL FLOW PATTERNS
        
        Conditional Logic:
        ```leo
        transition calculate_fee(amount: u64) -> u64 {
            let fee: u64 = 0u64;
            
            if amount < 100u64 {
                fee = 1u64;
            } else if amount < 1000u64 {
                fee = amount / 100u64;
            } else {
                fee = amount / 50u64;
            }
            
            return fee;
        }
        ```
        
        Loop with Constant Bounds:
        ```leo
        transition sum_array(values: [u64; 10]) -> u64 {
            let sum: u64 = 0u64;
            
            for i: u8 in 0u8..10u8 {
                sum = sum + values[i];
            }
            
            return sum;
        }
        ```

        SECURITY PATTERNS
        
        Access Control:
        ```leo
        transition admin_action(param: u64) -> u64 {
            assert_eq(self.signer, ADMIN);
            return param * 2u64;
        }
        ```
        
        Overflow Protection:
        ```leo
        // Checked arithmetic (default)
        let sum: u64 = a + b; // panics on overflow
        
        // Manual check
        assert(a + b >= a); // overflow check
        let sum: u64 = a + b;
        
        // Wrapped arithmetic
        let wrapped_sum: u64 = a.add_wrapped(b); // wraps on overflow
        ```

        COMMON PATTERNS BY USE CASE
        
        TOKEN:
        • Records for private balances
        • Mappings for public balances/supply
        • Transfer functions (private/public/cross)
        • Mint/burn with supply tracking
        
        NFT:
        • Records with metadata fields
        • Mapping for ownership registry
        • Unique token_id generation
        • Transfer with ownership verification
        
        DEFI:
        • Order structs for trades
        • Liquidity pool mappings
        • Price calculation functions
        • Slippage protection asserts
        
        VOTING:
        • Private vote records
        • Public tally mappings
        • Time-based phases (use block.height)
        • Merkle proof verification
        
        IDENTITY:
        • Credential records (private)
        • Attestation mappings (public)
        • Revocation checks
        • Multi-sig verification

        TYPE USAGE
        • u64 for amounts, balances, counts
        • u32 for indices, small numbers
        • u128 for large numbers, timestamps
        • field for hashes, commitments
        • address for accounts, contracts
        • bool for flags, conditions
        • Arrays [T; N] for fixed collections
        • Tuples (T1, T2) for multiple returns

        NAMING CONVENTIONS
        • Programs: lowercase_underscore
        • Constants: UPPER_SNAKE_CASE
        • Structs/Records: PascalCase
        • Functions: snake_case
        • Mappings: plural_noun
        • Async functions: update_*, add_*, remove_* (≤31 bytes!)

        GENERATE ONLY COMPILABLE CODE:
        
        IMPORTANT: Output ONLY pure Leo code without any markdown formatting.
        DO NOT include ```leo or ``` markers.
        DO NOT add explanatory comments outside the Leo code.
        Generate ONLY the program content that can be directly saved as a .leo file.
        """
                .formatted(projectName, description, admin, projectName, admin);

        return   BetaTextBlock.builder()
                .text(prompt)
                .citations(List.of())
                .build();
    }

    /**
     * Returns the Leo rules reference as a TextBlockParam with ephemeral cache control for re-use.
     */
    public static BetaTextBlockParam LeoRulesBookParam() {
        return BetaTextBlockParam.builder()
                .text(LeoRulesBook())
                .cacheControl(BetaCacheControlEphemeral.builder().build())
                .build();
    }

    /**
     * Validates if a string is a valid Aleo address format.
     * Valid addresses start with "aleo1" and are 63 characters long.
     */
    public static boolean isValidAleoAddress(String address) {
        return address != null 
            && address.startsWith("aleo1") 
            && address.length() == 63
            && address.matches("^aleo1[a-z0-9]{58}$");
    }

    /**
     * Returns a comment explaining the admin placeholder.
     */
    public static String getAdminPlaceholderComment() {
        return """
        // IMPORTANT: %s is a PLACEHOLDER address
        // Replace with a real Aleo address before deployment
        // Example: aleo1qnr4dkkvkgfqph0vzc3y6z2eu975wnpz2925ntjccd5cfqxtyu8sta57j8
        // Or remove admin functionality if not needed
        """.formatted(ADMIN_PLACEHOLDER);
    }
    
    /**
     * System prompt for Leo code correction with tools
     * 
     * IMPORTANT: Cache control is only available with beta messages.
     * Since we're using standard messages for the corrector (as per requirements),
     * we cannot cache the Leo rules here. To enable caching, we would need to 
     * switch to beta messages in LeoCodeCorrector.
     * 
     * @return Full system prompt with Leo rules (uncached)
     */
    public static String getLeoCorrectorSystemPrompt() {
        return LeoRulesBook() + """
        
        
        You are a Leo language expert debugger. Your task is to fix compilation errors in Leo code using the provided tools.
        
        IMPORTANT: You MUST use the tools to read, analyze and fix the code. Do not output code directly.
        
        Available tools:
        1. read_file_with_line_numbers - Read the entire Leo file to understand the code
        2. read_file_lines - Read specific lines around errors
        3. edit_file - Replace lines to fix errors
        
        When you receive an error message:
        1. First, use read_file_with_line_numbers to read the entire main.leo file
        2. Analyze the error messages carefully - Leo errors often point to specific line numbers
        3. Use edit_file to fix the errors by replacing the problematic lines
        4. Focus on one error at a time, starting with the first one
        
        Common Leo compilation errors and fixes:
        - "expected ; -- found X" - Add missing semicolon
        - "type X is not equal to type Y" - Fix type mismatches in assignments or function calls
        - "unknown variable X" - Variable might be misspelled or not declared
        - "cannot call function X" - Check function name, parameters, and visibility
        - "circuit X does not exist" - Ensure struct/record is properly defined
        - "expected X found Y" - Syntax error, fix the token at that position
        - "invalid address literal" - ALWAYS replace with: aleo1ufj2q79zyspekyrrtukmx9u8ruyjrcvm6pvsxjk42m48ejepcgysucum7x
        
        WORKFLOW:
        1. Read the error message to identify the file and line number
        2. Read the file to understand the context
        3. Identify the exact issue
        4. Use edit_file to fix it
        5. Report what you fixed
        
        Remember: ALWAYS use tools to read and edit files. Never output code directly in your response.
        """;
    }
    
    /**
     * Creates a user prompt for Leo code correction
     */
    public static BetaTextBlock getLeoCorrectorUserPrompt(String projectPath, String errorOutput, int attemptNumber) {
        String prompt = String.format("""
        Fix the Leo compilation errors in the project at: %s
        
        Attempt: %d/20
        
        Compilation error output:
        ```
        %s
        ```
        
        Please analyze and fix these errors using the available tools.
        The main Leo file is located at: %s/src/main.leo
        
        Use the tools to:
        1. Read the file to understand the code
        2. Identify the issues based on the error messages
        3. Edit the file to fix the errors
        
        Focus on fixing the errors one by one, starting with the first error in the list.
        """, projectPath, attemptNumber, errorOutput, projectPath);
        
        return BetaTextBlock.builder()
                .text(prompt)
                .citations(List.of())
                .build();
    }
}
