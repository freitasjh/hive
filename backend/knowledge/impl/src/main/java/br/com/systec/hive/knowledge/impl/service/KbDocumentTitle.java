package br.com.systec.hive.knowledge.impl.service;

import dev.langchain4j.data.document.Metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The one line that tells a chunk what it is a chunk <i>of</i>.
 *
 * <h3>Why a chunk cannot stand on its own without it</h3>
 *
 * <p>A segment cut out of the middle of a document is a sentence fragment. Handed to the LLM as
 * retrieved context, the fragment has no subject, so the model completes it from its own priors
 * rather than from the document &mdash; which is the mechanism LangChain4j's own RAG tutorial names
 * when it warns that a segment without enough context makes the LLM misread it and hallucinate.
 * The title is the cheapest thing that repairs this: it re-attaches a subject to every fragment,
 * including the two that carry none of their own &mdash; the cover page and the copyright page, each
 * of which is meaningless alone and each of which lands in its own chunk.
 *
 * <h3>Where the title comes from, in order</h3>
 *
 * <ol>
 *   <li><b>the parser's metadata</b> &mdash; Apache Tika fills {@code dc:title} / {@code title} from
 *       the document itself (PDF {@code /Title}, DOCX core properties, HTML {@code <title>}). When
 *       the ingested document knows its own title, that is the authoritative answer and no guess
 *       is needed. Keys are consulted in a fixed order and matched on the last {@code :}-separated
 *       segment, so {@code subtitle} cannot win and the winner cannot depend on map ordering
 *       (see {@link #TITLE_KEYS}).</li>
 *   <li><b>the first meaningful line of the text</b> &mdash; for the many PDFs that ship with an
 *       empty {@code /Title} but a title typeset on the cover. This is the case that produced the
 *       chunks quoted in the RAG spec, and it is why the fallback exists at all.</li>
 *   <li><b>the file name</b> &mdash; what the project used unconditionally before. Never nothing:
 *       a chunk with no subject at all is strictly worse than one with a poor subject.</li>
 * </ol>
 *
 * <p>Deliberately not a real title extractor. Tika already runs a full parse of the document; a
 * font-size or bold-weight heuristic on top of it would be a second, worse parser to maintain for a
 * line of prefix text.
 */
public final class KbDocumentTitle {

    /** A "first meaningful line" longer than this is body text, not a heading. */
    private static final int MAX_TITLE_LENGTH = 200;

    private KbDocumentTitle() {
    }

    /**
     * Resolves the title for a parsed document.
     *
     * @param metadata parser metadata, may be null
     * @param text     extracted text, may be null
     * @param fileName the stored file name, the last resort
     */
    public static String resolve(Metadata metadata, String text, String fileName) {
        String fromMetadata = fromMetadata(metadata);
        if (fromMetadata != null) {
            return fromMetadata;
        }
        String fromText = firstMeaningfulLine(text);
        if (fromText != null) {
            return fromText;
        }
        return fileName == null ? "" : fileName.strip();
    }

    /**
     * Keys consulted, in this order, when the parser reports a title.
     *
     * <p>An ordered list rather than a scan, for two reasons that both bite in practice.
     *
     * <p><b>Correctness of the match.</b> Matching a key by {@code endsWith("title")} also matches
     * {@code subtitle}, {@code dc:description-subtitle} and {@code x-custom:chapTitle} — verified
     * against {@code String.endsWith}. A DOCX carrying {@code dc:title="Guia"} and
     * {@code subtitle="Edição 2024"} can then have its edition promoted to be the document's title,
     * on every chunk. Matching the last {@code :}-separated segment against the exact word
     * {@code title} rejects all three.
     *
     * <p><b>Determinism of the winner.</b> {@code Metadata.toMap()} returns {@code new HashMap<>(…)}
     * (langchain4j-core 1.19.0, {@code Metadata.java}), so iterating it visits keys in
     * hash-bucket order. With two matching keys the winner would be whichever landed first, chosen
     * by the hash of the key set — so adding an unrelated piece of metadata to a document could
     * change which title every one of its chunks carries. An ordered list has no such dependence.
     */
    private static final List<String> TITLE_KEYS = List.of("dc:title", "dcterms:title", "meta:title", "title");

    private static String fromMetadata(Metadata metadata) {
        if (metadata == null) {
            return null;
        }
        for (String key : TITLE_KEYS) {
            String cleaned = clean(metadata.getString(key));
            if (cleaned != null) {
                return cleaned;
            }
        }
        return fromUnprefixedTitleKey(metadata);
    }

    /**
     * Last resort inside the metadata: a title under a prefix this project does not name.
     *
     * <p>Only when exactly one such key carries a usable value. With two of them there is no
     * principled way to choose, and picking one would reintroduce exactly the hash-order
     * dependence {@link #TITLE_KEYS} exists to remove — so the document falls through to its first
     * line, which is a real answer rather than a coin toss.
     */
    private static String fromUnprefixedTitleKey(Metadata metadata) {
        String found = null;
        for (Map.Entry<String, Object> entry : metadata.toMap().entrySet()) {
            if (!isTitleKey(entry.getKey()) || !(entry.getValue() instanceof String text)) {
                continue;
            }
            String cleaned = clean(text);
            if (cleaned == null) {
                continue;
            }
            if (found != null && !found.equals(cleaned)) {
                return null;
            }
            found = cleaned;
        }
        return found;
    }

    /** Whether the key's last {@code :}-separated segment is exactly {@code title}. */
    private static boolean isTitleKey(String key) {
        if (key == null) {
            return false;
        }
        int lastSeparator = key.lastIndexOf(':');
        String segment = lastSeparator < 0 ? key : key.substring(lastSeparator + 1);
        return "title".equalsIgnoreCase(segment.strip());
    }

    /**
     * The <b>first</b> non-blank line, and only that one.
     *
     * <p>Not a search for the first line that looks like a title. If the opening line is a
     * paragraph, a page number or a link, the document has no typeset title and the honest answer
     * is the file name; picking up the second line instead would label every chunk of a body of
     * prose with whatever happened to be in its second row.
     */
    private static String firstMeaningfulLine(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .map(KbDocumentTitle::clean)
                .orElse(null);
    }

    /**
     * Normalises a candidate title, or rejects it.
     *
     * <p>Rejected: blank, over {@value #MAX_TITLE_LENGTH} characters (that is a paragraph, and
     * prefixing every chunk with a paragraph is worse than prefixing none), a bare page number, and
     * anything that is only a URL &mdash; a link is not a subject, and a document whose cover line
     * is a link should fall through to the file name.
     */
    private static String clean(String candidate) {
        if (candidate == null) {
            return null;
        }
        String cleaned = candidate.strip().replaceAll("\\s+", " ");
        if (cleaned.isEmpty() || cleaned.length() > MAX_TITLE_LENGTH) {
            return null;
        }
        if (cleaned.matches("[\\d\\p{Punct}\\s]+")) {
            return null;
        }
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            return null;
        }
        return cleaned;
    }

    /**
     * The header block every chunk is prefixed with: the title, and the file name underneath it
     * when the two differ.
     *
     * <p>Both, not either. The file name is what the project already put on every chunk and what
     * the citation resolves against; the title is the part that carries meaning. When a document's
     * title happens to be its file name &mdash; a {@code .txt} upload, mostly &mdash; the block
     * collapses to one line instead of repeating itself.
     */
    public static String header(String title, String fileName) {
        List<String> lines = new ArrayList<>(2);
        String normalizedTitle = title == null ? "" : title.strip();
        if (!normalizedTitle.isEmpty()) {
            lines.add(normalizedTitle);
        }
        String normalizedFileName = fileName == null ? "" : fileName.strip();
        if (!normalizedFileName.isEmpty() && !normalizedFileName.equals(normalizedTitle)) {
            lines.add(normalizedFileName);
        }
        return String.join("\n", lines);
    }
}
