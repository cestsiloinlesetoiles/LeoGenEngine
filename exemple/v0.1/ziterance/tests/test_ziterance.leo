// The 'test_ziterance' test program.
import ziterance.aleo;
program test_ziterance.aleo {
    @test
    script test_it() {
        let result: u32 = ziterance.aleo/main(1u32, 2u32);
        assert_eq(result, 3u32);
    }

    @test
    @should_fail
    transition do_nothing() {
        let result: u32 = ziterance.aleo/main(2u32, 3u32);
        assert_eq(result, 3u32);
    }
}
