package br.com.systec.hive.knowledge.impl.service;

import java.util.ArrayList;
import java.util.List;

public final class KbTextSplitter {

    public static final int CHUNK_TOKENS = 512;
    public static final int CHUNK_OVERLAP = 50;
    private static final int CHARS_PER_TOKEN = 4;

    private KbTextSplitter() {
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    public static List<String> split(String title, String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String prefix = (title == null || title.isBlank()) ? "" : title.strip() + "\n\n";
        int budget = CHUNK_TOKENS * CHARS_PER_TOKEN - prefix.length();
        int overlap = CHUNK_OVERLAP * CHARS_PER_TOKEN;
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder(prefix);
        int currentLen = prefix.length();
        List<String> currentWords = new ArrayList<>();
        for (String word : words) {
            if (currentLen + word.length() + 1 > prefix.length() + budget && !currentWords.isEmpty()) {
                chunks.add(current.toString().strip());
                String overlapTail = tailWords(currentWords, overlap);
                current = new StringBuilder(prefix).append(overlapTail);
                currentLen = current.length();
                currentWords = new ArrayList<>(List.of(overlapTail.isBlank() ? new String[0] : overlapTail.split("\\s+")));
                if (currentWords.size() == 1 && currentWords.get(0).isEmpty()) {
                    currentWords = new ArrayList<>();
                }
            }
            current.append(word).append(' ');
            currentLen += word.length() + 1;
            currentWords.add(word);
        }
        if (!currentWords.isEmpty()) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    private static String tailWords(List<String> words, int charBudget) {
        StringBuilder sb = new StringBuilder();
        for (int i = words.size() - 1; i >= 0 && sb.length() < charBudget; i--) {
            sb.insert(0, words.get(i) + " ");
        }
        return sb.toString();
    }
}
