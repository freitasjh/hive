package br.com.systec.hive.assistant.impl.diagnostics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** MET-10: buckets excludentes para `langfuse.observation.usage_details`. */
class UsageBucketsNormalizerTest {

    @Test
    void when_providerExposesCachedTokens_then_inputIsExclusiveAndCacheHasItsOwnBucket() {
        UsageBucketsNormalizer.Buckets buckets = UsageBucketsNormalizer.of(17903, 17817, 188, 18091);

        assertThat(buckets.input()).isEqualTo(86);
        assertThat(buckets.output()).isEqualTo(188);
        assertThat(buckets.inputCachedTokens()).isEqualTo(17817);
        assertThat(buckets.total()).isEqualTo(18091);
        assertThat(buckets.usageDetails()).isEqualTo(
                "{\"input\":86,\"output\":188,\"input_cached_tokens\":17817,\"total\":18091}");
    }

    @Test
    void when_providerHasNoCache_then_cacheKeyIsAbsent() {
        UsageBucketsNormalizer.Buckets buckets = UsageBucketsNormalizer.of(10, null, 5, 15);

        assertThat(buckets.input()).isEqualTo(10);
        assertThat(buckets.output()).isEqualTo(5);
        assertThat(buckets.inputCachedTokens()).isNull();
        assertThat(buckets.total()).isEqualTo(15);
        assertThat(buckets.usageDetails()).isEqualTo("{\"input\":10,\"output\":5,\"total\":15}");
    }

    @Test
    void when_cachedTokensIsZero_then_itIsTreatedAsNoCache() {
        assertThat(UsageBucketsNormalizer.of(10, 0, 5, 15).usageDetails())
                .isEqualTo("{\"input\":10,\"output\":5,\"total\":15}");
    }

    @Test
    void when_cachedTokensExceedPrompt_then_cacheIsClampedAndInputNeverNegative() {
        UsageBucketsNormalizer.Buckets buckets = UsageBucketsNormalizer.of(10, 30, 5, 15);

        assertThat(buckets.input()).isZero();
        assertThat(buckets.inputCachedTokens()).isEqualTo(10);
        assertThat(buckets.total()).isEqualTo(15);
    }

    @Test
    void when_bucketsAreBuilt_then_noTokenIsCountedTwice() {
        UsageBucketsNormalizer.Buckets withCache = UsageBucketsNormalizer.of(17903, 17817, 188, 18091);
        assertThat(withCache.input() + withCache.output() + withCache.inputCachedTokens())
                .isEqualTo(withCache.total());

        UsageBucketsNormalizer.Buckets plain = UsageBucketsNormalizer.of(10, null, 5, 15);
        assertThat(plain.input() + plain.output()).isEqualTo(plain.total());
    }

    @Test
    void when_usageDetailsIsSerialized_then_noCostKeyAppears() {
        String json = UsageBucketsNormalizer.of(17903, 17817, 188, 18091).usageDetails();

        assertThat(json).doesNotContain("cost", "price", "sk-", "gen_ai");
    }
}
