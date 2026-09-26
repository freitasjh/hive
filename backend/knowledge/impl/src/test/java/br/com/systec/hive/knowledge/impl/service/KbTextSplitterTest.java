package br.com.systec.hive.knowledge.impl.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KbTextSplitterTest {

    @Test
    void when_split_with_longText_then_chunksWithOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("word").append(i).append(' ');
        }
        List<String> chunks = KbTextSplitter.split("Title", sb.toString());
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0)).startsWith("Title");
        for (String chunk : chunks) {
            assertThat(KbTextSplitter.estimateTokens(chunk)).isLessThanOrEqualTo(600);
        }
        String[] firstWords = chunks.get(0).split("\\s+");
        String lastOfFirst = firstWords[firstWords.length - 1];
        assertThat(chunks.get(1)).contains(lastOfFirst);
    }

    @Test
    void when_split_with_blank_then_empty() {
        assertThat(KbTextSplitter.split("T", "   ")).isEmpty();
        assertThat(KbTextSplitter.split("T", null)).isEmpty();
    }

    @Test
    void when_split_with_shortText_then_singleChunkWithTitle() {
        List<String> chunks = KbTextSplitter.split("Doc", "hello world");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("Doc").contains("hello world");
    }
}
