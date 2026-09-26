package br.com.systec.hive.commons.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails a build when a similarity cut-off is written into a class that should only be <em>passing
 * one on</em>.
 *
 * <h3>Why this exists as a shared class and not as a line in two test files</h3>
 *
 * <p>The cut-off used to be a constant in two places at once, neither of which knew which model it
 * applied to, so it was silently wrong for a model whose scores sit below it. That defect cannot be
 * prevented by a comment, only by a check — and a check duplicated across Maven modules is a check
 * that is correct in one place and quietly wrong in the other. So the pattern, the assertion and
 * the pattern's own positive control live here, once, and every consumer test calls
 * {@link #assertNoHardcodedCutOff(Path)}.
 *
 * <h3>What counts as a cut-off</h3>
 *
 * <p>{@code 0.7}, {@code 0.70} and {@code 0.700} — the same number written three ways, which is
 * exactly how it slips past a naive check. {@code 0.75} and {@code 10.7} are different numbers and
 * are left alone: a guard that flags those is a guard people disable.
 *
 * <p><b>This class must never be trusted without its own test.</b> A regex that silently matches
 * nothing makes every consumer green forever while proving nothing, so
 * {@code HardcodedSimilarityCutOffGuardTest} in this module pins both directions: the pattern fires
 * on a hardcoded cut-off and stays quiet on a legitimate line, and the assertion actually fails on a
 * file that contains one. A consumer test that reports "clean" is only meaningful because that test
 * exists and runs in the same reactor.
 */
public final class HardcodedSimilarityCutOffGuard {

    /**
     * A similarity cut-off written as a literal, in any of its three spellings.
     *
     * <p>Lookbehind rejects a digit or a dot before the {@code 0}, so {@code 10.7} and {@code 1.07}
     * are not cut-offs. The optional {@code 0+} plus the trailing lookahead make {@code 0.70} and
     * {@code 0.700} match while leaving {@code 0.75}–{@code 0.79}, {@code 0.7001} and a chained
     * {@code 0.7.5} alone.
     */
    public static final Pattern HARDCODED_CUT_OFF = Pattern.compile("(?<![\\d.])0\\.7(?:0+)?(?![\\d.])");

    private HardcodedSimilarityCutOffGuard() {
    }

    /** Whether {@code line} contains a hardcoded cut-off. Exposed so a test can pin the pattern. */
    public static boolean isHardcoded(String line) {
        return line != null && HARDCODED_CUT_OFF.matcher(line).find();
    }

    /**
     * Asserts that {@code file} names no hardcoded cut-off, line by line.
     *
     * <p>Checks the file exists first: a guard pointed at a path that moved scans nothing and passes
     * forever, which is the failure mode a reviewer has to catch by reading the test.
     */
    public static void assertNoHardcodedCutOff(Path file) throws IOException {
        assertThat(Files.isRegularFile(file))
                .as("the guard is worthless if it silently scans nothing: %s", file.toAbsolutePath())
                .isTrue();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            assertThat(isHardcoded(lines.get(i)))
                    .as("%s:%d hardcodes a similarity cut-off — the number belongs to "
                            + "EmbeddingTaskConvention, resolved from the base's model",
                            file.getFileName(), i + 1)
                    .isFalse();
        }
    }
}
