// The 'test_sustainability_verify' test program.
import sustainability_verify.aleo;
program test_sustainability_verify.aleo {
    @test
    script test_it() {
        let result: u32 = sustainability_verify.aleo/main(1u32, 2u32);
        assert_eq(result, 3u32);
    }

    @test
    @should_fail
    transition do_nothing() {
        let result: u32 = sustainability_verify.aleo/main(2u32, 3u32);
        assert_eq(result, 3u32);
    }
}
