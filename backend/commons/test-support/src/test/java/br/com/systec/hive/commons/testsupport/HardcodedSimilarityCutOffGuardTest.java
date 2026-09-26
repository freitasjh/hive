package br.com.systec.hive.commons.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guard's own correctness, pinned in both directions.
 *
 * <p>This is the test that makes every consumer's "clean" result mean something. A pattern that
 * matches nothing — the previous one did not match {@code 0.70}, which is the spelling actually used
 * in production — leaves every consumer green while checking nothing, and the only way to tell the
 * two states apart is to fire it at a known-bad line on purpose.
 */
class HardcodedSimilarityCutOffGuardTest {

    @Test
    void when_aCutOffIsWrittenInAnySpelling_then_itIsCaught() {
        for (String line : new String[]{
                "private static final double DEFAULT_MIN_SCORE = 0.7;",
                "public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.70;",
                "private static final double THRESHOLD = 0.700;",
                "if (score >= 0.7) {",
                "return minScore > 0 ? minScore : 0.70;",
                "private static final float CUT = 0.7f;",
                // Deliberately blunt: the pattern cannot tell a cut-off from a temperature, and does
                // not try. The classes it guards decide retrieval and hold no temperature, so a false
                // alarm there would be a real finding, not noise.
                "agent.setTemperature(0.7);"}) {
            assertThat(HardcodedSimilarityCutOffGuard.isHardcoded(line))
                    .as("must be caught: %s", line)
                    .isTrue();
        }
    }

    @Test
    void when_theLineHoldsADifferentNumber_then_itIsLeftAlone() {
        for (String line : new String[]{
                "private static final double NOMIC_SIMILARITY_THRESHOLD = 0.60;",
                "private static final double A = 0.75;",
                "private static final double B = 0.79;",
                "private static final double C = 0.85;",
                "private static final int TEN = 10.7;",
                "private static final double SMALL = 0.07;",
                "private static final double NEAR = 0.7001;",
                "private static final double CHAINED = 0.7.5;",
                "return retrieval.threshold() != null ? retrieval.threshold() : 0.6;"}) {
            assertThat(HardcodedSimilarityCutOffGuard.isHardcoded(line))
                    .as("must NOT be caught: %s", line)
                    .isFalse();
        }
    }

    @Test
    void when_theFileContainsACutOff_then_theAssertionFails() throws IOException {
        Path file = Files.writeString(temp.resolve("Bad.java"),
                "class Bad {\n    static final double T = 0.70;\n}\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> HardcodedSimilarityCutOffGuard.assertNoHardcodedCutOff(file))
                .as("a guard that cannot fail is not a guard")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Bad.java:2");
    }

    @Test
    void when_theFileIsClean_then_theAssertionPasses() throws IOException {
        Path file = Files.writeString(temp.resolve("Good.java"),
                "class Good {\n    static final double T = 0.60;\n}\n", StandardCharsets.UTF_8);

        HardcodedSimilarityCutOffGuard.assertNoHardcodedCutOff(file);
    }

    @Test
    void when_theFileIsMissing_then_theAssertionFailsRatherThanPassingQuietly() {
        Path missing = temp.resolve("NotThere.java");

        assertThatThrownBy(() -> HardcodedSimilarityCutOffGuard.assertNoHardcodedCutOff(missing))
                .as("scanning nothing must never read as clean")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("silently scans nothing");
    }

    @TempDir
    Path temp;
}
