package br.com.systec.hive.knowledge.impl.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The unit the chunk budget is expressed in.
 *
 * <p>These exist because the bug this change fixes was a unit bug: 512 is a token count and was
 * being enforced in characters. A character count of a text is the one number every implementation
 * agrees on, so an assertion like "the chunk is under 2048 characters" cannot tell a token-aware
 * splitter from a character-based one &mdash; it was written, it passed against the old code, and it
 * guarded nothing. What distinguishes them is how the count <b>reacts to the script</b>: a
 * {@code length() / 4} estimate is flat across every language by construction, a real tokenizer is
 * not.
 */
class KbTokenCountEstimatorTest {

    private final KbTokenCountEstimator estimator = KbTokenCountEstimator.getInstance();

    private static String repeated(String unit, int times) {
        return unit.repeat(times);
    }

    @Test
    void when_estimateTokens_withNullOrEmpty_then_zero() {
        assertThat(estimator.estimateTokenCountInText(null)).isZero();
        assertThat(estimator.estimateTokenCountInText("")).isZero();
    }

    @Test
    void when_estimateTokens_then_itIsNotTheCharacterQuarter() {
        String portuguese = repeated("A etapa descreve como o operador reinicia o indice vetorial. ", 40);

        int characterQuarter = portuguese.length() / 4;

        assertThat(estimator.estimateTokenCountInText(portuguese))
                .as("Portuguese runs well under 4 characters per token")
                .isGreaterThan(characterQuarter);
    }

    /**
     * The sharpest form of the same point. CJK is close to one token per character, so a
     * {@code length() / 4} estimate undercounts it by roughly 4x — and a budget built on that
     * estimate would silently quadruple the size of every chunk in that script.
     */
    @Test
    void when_estimateTokens_withCjk_then_theCountTracksTheScriptNotTheCharacterQuarter() {
        String cjk = repeated("検索対象の文書を分割する。", 50);

        assertThat(estimator.estimateTokenCountInText(cjk))
                .isGreaterThan(cjk.length() / 4 * 2);
    }

    /** Same length, different script, different count — impossible for a character-based estimate. */
    @Test
    void when_estimateTokens_withSameLengthInTwoScripts_then_theCountsDiffer() {
        String latin = repeated("word ", 100);
        String cjk = repeated("検索", 250);

        assertThat(latin).hasSameSizeAs(cjk);
        assertThat(estimator.estimateTokenCountInText(cjk))
                .isNotEqualTo(estimator.estimateTokenCountInText(latin));
    }

    @Test
    void when_estimateTokens_then_moreTextMeansMoreTokens() {
        String one = repeated("palavra ", 10);
        String two = repeated("palavra ", 100);

        assertThat(estimator.estimateTokenCountInText(two))
                .isGreaterThan(estimator.estimateTokenCountInText(one));
    }

    /** The message overloads are on the interface, so they must not quietly count characters. */
    @Test
    void when_estimateTokensInMessages_then_itCountsTheMessageText() {
        var messages = java.util.List.of(
                SystemMessage.from("You are a helpful assistant."),
                UserMessage.from("How do I restart the index?"));

        int total = estimator.estimateTokenCountInMessages(messages);

        assertThat(total)
                .isEqualTo(estimator.estimateTokenCountInMessage(messages.get(0))
                        + estimator.estimateTokenCountInMessage(messages.get(1)))
                .isPositive();
    }

    @Test
    void when_estimateTokensInMessages_withNull_then_zero() {
        assertThat(estimator.estimateTokenCountInMessages(null)).isZero();
        assertThat(estimator.estimateTokenCountInMessage(null)).isZero();
    }
}
