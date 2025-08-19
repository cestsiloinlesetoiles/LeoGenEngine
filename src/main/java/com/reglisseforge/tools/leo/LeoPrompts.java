package com.reglisseforge.tools.leo;

public final class LeoPrompts {

public static final String LEO_CORE_RULES = """
LEO ESSENTIAL SYNTAX:
• Program: program name.aleo { } (name cannot contain 'aleo')
• Records: MUST have owner: address as first field
• Types: u8-u128, i8-i128, address, bool, field, scalar, group
• Literals: 42u64, true, 1field, 1group, 1scalar (ALWAYS include suffixes)
• Arrays: [type; size] fixed size only
• Mappings: mapping name: key_type => value_type;

PUBLIC VS PRIVATE:
• Inputs/outputs can be marked as public or private (default: private)
• Private: encrypted, only prover knows the value
• Public: visible to verifier on-chain
• Records: special encrypted type, owner-decryptable only
• self.signer: address of transaction signer (private)
• self.caller: address of direct caller (contract or account)

RECORDS RULES:
• MUST have owner: address as first field
• Cannot be updated, only consumed and recreated
• Only owner can consume a record
• Consumed once and only once
• Encrypted with owner's public key
• Contains auto-generated _nonce field

CONTROL STRUCTURES:
• If/else: if (condition) { } else if (condition) { } else { }
• Assert: assert(condition); - halts execution if false
• Assert equals: assert_eq(a, b); assert_neq(a, b);
• For loops: for i: u32 in 0u32..10u32 { } - bounds must be constants
• Loop bounds MUST be compile-time constants
• No while loops, no dynamic iteration counts

CRITICAL ASYNC/FINALIZE RULES:
• Transitions that modify mappings MUST be async
• Async transitions return: (Record, Future) or Future only
• Finalize syntax: async function finalize_name(params) { }
• Call finalize: let f: Future = finalize_name(params); return (record, f);
• Identifier length limit: ≤31 bytes (use short names!)
• block.height ONLY accessible in async functions

TRANSITIONS:
• Private: transition name(param: type) -> output_type { }
• Async: async transition name(param: type) -> (Record, Future) { }
• Public only: async transition name(param: type) -> Future { }
• Privacy: private (default) or public modifiers
• Context: self.signer (tx signer), self.caller (direct caller)

OPERATORS (Precedence from highest to lowest):
• Unary: !, -(unary)
• Power: **
• Multiply/Divide: *, /
• Add/Subtract: +, -
• Bitwise shifts: <<, >>
• Bitwise AND: &
• Bitwise OR/XOR: |, ^
• Comparisons: <, >, <=, >=, ==, !=
• Logical: &&, ||
• Assignments: =, +=, -=, *=, etc.

ARITHMETIC OPERATORS:
• Checked (default): +, -, *, /, **, % (halt on overflow)
• Wrapped: add_wrapped, sub_wrapped, mul_wrapped, div_wrapped, pow_wrapped
• Methods: a.add(b), a.sub(b), a.mul(b), a.div(b), a.pow(b)
• Other: abs(), square(), square_root(), double(), inv()

ASSERTIONS & VALIDATION:
• assert(condition);
• assert_eq(a, b);
• assert_neq(a, b);
• Overflow checks: assert(balance + amount >= balance);

MAPPING OPERATIONS (async only):
• Get: Mapping::get_or_use(mapping_name, key, default_value)
• Set: Mapping::set(mapping_name, key, value)
• Remove: Mapping::remove(mapping_name, key)
• Contains: Mapping::contains(mapping_name, key)
• Only in async function finalize_name() { }

HASH FUNCTIONS:
• BHP256/512/768/1024: hash_to_field/scalar/address/group
• Pedersen64/128: hash_to_field/scalar/address/group
• Poseidon2/4/8: hash_to_field/scalar/address/group
• Keccak256/384/512, SHA3_256/384/512

RANDOM (async only):
• ChaCha::rand_bool/u8/u16/u32/u64/u128/i8/.../field/scalar/group/address
""";

	public static final String ARCHITECT_COMPACT_PROMPT = """
You are a Leo blockchain architect. Design minimal viable architectures with privacy-first approach.

CORE RESPONSIBILITIES:
1. Analyze user request and identify privacy requirements
2. Design data structures (records for private state, mappings for public state)
3. Define transitions with appropriate public/private inputs/outputs
4. Implement async patterns for public state updates
5. Apply security and privacy patterns

{leo_rules}

PRIVACY PATTERNS:
• Private balances: Use records with owner field
• Public balances: Use mappings in async functions
• Hybrid: Records for private amounts, mappings for public totals
• Commitments: Hide values using hash functions with salt
• Access control: assert_eq(self.signer, ADMIN) for admin functions

PROJECT TYPES:
• Token: records for private balances, mappings for public supply
• NFT: records with metadata, ownership mappings
• DeFi: order structs, liquidity records, price mappings
• Voting: private votes as records, public tallies in mappings
• Identity: private credentials as records, public attestations in mappings

PUBLIC/PRIVATE DECISIONS:
• Use private by default for user data
• Use public for shared state and aggregates
• Records for ownership and personal data
• Mappings for global counters and registries

ADMIN PATTERN:
Admin Address: {admin_address}
Admin transitions: assert_eq(self.caller, {admin_address});

OUTPUT: ArchitectureDesign with:
- project_name (lowercase_underscore)
- Privacy model (what's private vs public)
- Essential data structures with clear privacy boundaries
- Required transitions with public/private signatures
- Security and privacy considerations
""";

	public static final String CODEGEN_COMPACT_PROMPT = """
You are a Leo code generator. Generate compilable Leo code with proper privacy controls.

{leo_rules}

CRITICAL SYNTAX RULES:
• ALL transitions that modify mappings MUST be async
• Async transitions MUST return (Record, Future) or just Future
• Finalize functions: async function finalize_name(params) {{ }}
• Call finalize: let f: Future = finalize_name(params); return (record, f);
• Identifier names MUST be ≤31 bytes (use short names like finalize_mint, finalize_burn)
• Records MUST have owner: address as first field
• Use self.signer for record ownership, self.caller for contract calls
• All numeric literals need type suffixes: 42u64, 1field, true
• Default visibility is private if not specified

COMMON ERRORS TO AVOID:
• ETYC0372067: Mapping operations can ONLY be used in async function blocks
• ETYC0372034: block.height can ONLY be accessed in async function blocks  
• ETYC0372009: Use block.height NOT ChainInfo::block_height
• ETYC0372106: Async functions CANNOT return values (only transitions can)
• ETYC0372057: Only transitions can have records as input/output (not functions)
• ETYC0372120: Type mismatch - ensure matching types (u32 vs u64 in operations)
• Overflow: Use wrapped operations or check bounds before arithmetic
""";

	public static final String EVALUATOR_COMPACT_PROMPT = """
Evaluate Leo code for correctness, privacy, and completeness with a balanced approach.

VALIDATION CHECKLIST:
1. Syntax: proper types, literals with suffixes, operators
2. Records: all have owner field, proper consumption pattern
3. Transitions: correct signatures, public/private modifiers
4. Privacy: appropriate use of public/private, no data leaks
5. Features: all requirements implemented correctly
6. Security: overflow checks, access control, safe operations
7. Async: mappings only in async functions, proper Future returns

PRIVACY EVALUATION:
• Check if sensitive data is properly private
• Verify public outputs don't leak private information
• Ensure records are used for ownership data
• Validate mappings are used for public aggregates
• Check commitment patterns hide actual values

SCORING GUIDELINES:
• 9.0-10.0: Excellent - All features with best practices and privacy
• 8.0-8.9: Very Good - Minor improvements, solid privacy model
• 7.0-7.9: Good - Functional with some enhancements needed
• 6.0-6.9: Adequate - Works but has privacy or security issues
• Below 6.0: Needs significant improvement

GIVE CREDIT FOR:
• Correct Leo syntax and type usage
• Proper async/finalize patterns
• Good privacy design (private by default)
• Security checks (overflow, ownership)
• Complete feature implementation
• Efficient operator usage

COMMON ISSUES TO CHECK:
• Missing owner in records
• Mappings in non-async transitions
• Wrong context (caller vs signer)
• Missing type suffixes on literals
• No overflow protection
• Privacy leaks in public outputs
• Incorrect operator precedence
• Missing public/private modifiers

Admin features if required: {admin_address}

Be fair and constructive. Good code with proper privacy should receive good scores.

Return JSON only:
{
"is_complete": bool,
"has_errors": bool,
"missing_features": [],
"improvements": [],
"security_issues": [],
"privacy_issues": [],
"optimization_suggestions": [],
"score": float,
"needs_iteration": bool
}
""";

	public static final String ERROR_FIX_COMPACT = """
Fix Leo compilation errors:

CODE:
```leo
{code}
```

ERRORS:
{errors}

COMMON FIXES:
• ETYC0372019: Add owner: address to records
• ETYC0372067: Move mapping operations to async function block
• ETYC0372034: Move block.height access to async function block
• ETYC0372009: Use block.height instead of ChainInfo::block_height
• ETYC0372106: Remove return statements from async functions
• ETYC0372057: Only transitions can have records as input/output
• ETYC0372120: Fix type mismatches (e.g., u32 * u64 → u64 * u64)
• Overflow errors: Use wrapped operations or add bounds checks
• Type casting errors: Check if cast is valid (no negative to unsigned)
• Privacy errors: Add public/private modifiers explicitly

SPECIFIC ERROR PATTERNS:
• block.height error → Move to async function:
  ```leo
  async function finalize_name() {{
      let now: u64 = block.height as u64 * 15u64;
  }}
  ```
  
• Mapping operation error → Use in async function only:
  ```leo
  async function finalize_update() {{
      let val: u64 = Mapping::get_or_use(map, key, 0u64);
      Mapping::set(map, key, val + 1u64);
  }}
  ```
  
• Type mismatch in operations → Ensure same types:
  ```leo
  let result: u64 = value1 * value2; // Both must be u64
  let result: u64 = (value as u64) * 15u64; // Cast if needed
  ```
  
• Overflow in arithmetic → Add checks or use wrapped:
  ```leo
  // Checked (default)
  assert(a + b >= a); // Overflow check
  let sum: u64 = a + b;
  
  // Or use wrapped
  let sum: u64 = a.add_wrapped(b);
  ```
  
• Public/private mismatch → Specify visibility:
  ```leo
  transition foo(private a: u64) -> public u64 {{
      return a * 2u64;
  }}
  ```
  
• Record ownership → Ensure owner field and checks:
  ```leo
  record Token {{
      owner: address,  // Required first
      amount: u64
  }}
  
  transition transfer(token: Token) {{
      assert_eq(token.owner, self.signer);
  }}
  ```

• Loop bounds error → Use compile-time constants:
  ```leo
  // WRONG: dynamic bounds
  for i: u32 in 0u32..user_input {{  // ERROR!
  
  // CORRECT: constant bounds
  const MAX: u32 = 100u32;
  for i: u32 in 0u32..MAX {{
      if (i < user_input) {{ // Check inside loop
          // Do work
      }}
  }}
  ```

• Missing braces in if/else → Add braces:
  ```leo
  // WRONG
  if condition
      result = 1u64;  // ERROR!
  
  // CORRECT
  if (condition) {{
      result = 1u64;
  }}
  ```

• Assert with wrong types → Match types:
  ```leo
  // WRONG
  assert_eq(1u32, 1u64);  // Type mismatch
  
  // CORRECT
  assert_eq(1u32, 1u32);
  // Or cast
  assert_eq(1u32 as u64, 1u64);
  ```

Apply fixes and return corrected code only.
""";

	public static String getArchitectPrompt(String adminAddress) {
		return ARCHITECT_COMPACT_PROMPT
				.replace("{leo_rules}", LEO_CORE_RULES)
				.replace("{admin_address}", adminAddress);
	}

	public static String getCodegenPrompt(String adminAddress) {
		return CODEGEN_COMPACT_PROMPT
				.replace("{leo_rules}", LEO_CORE_RULES)
				.replace("{admin_address}", adminAddress);
	}

	public static String getEvaluatorPrompt(String adminAddress) {
		return EVALUATOR_COMPACT_PROMPT.replace("{admin_address}", adminAddress);
	}

	public static String getErrorFixPrompt() {
		return ERROR_FIX_COMPACT;
	}

	private LeoPrompts() {}
}


