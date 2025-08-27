// The 'test_zkpay_mobile' test program.
import zkpay_mobile.aleo;
program test_zkpay_mobile.aleo {
    @test
    script test_it() {
        let result: u32 = zkpay_mobile.aleo/main(1u32, 2u32);
        assert_eq(result, 3u32);
    }

    @test
    @should_fail
    transition do_nothing() {
        let result: u32 = zkpay_mobile.aleo/main(2u32, 3u32);
        assert_eq(result, 3u32);
    }
}
