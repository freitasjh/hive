package br.com.systec.hive.provider.api;

import br.com.systec.hive.provider.api.exception.ModelNotSupportedForProviderException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class VendorCatalog {

    /**
     * Base URLs fixas dos vendors OpenCode (GO/Zen). Nunca vêm do tenant.
     * <p>
     * O segmento {@code /v1} é OBRIGATÓRIO: o LangChain4j {@code OpenAiChatModel} concatena
     * apenas {@code /chat/completions} ao baseUrl (o default dele é
     * {@code https://api.openai.com/v1}). Sem o {@code /v1}, o POST cai em 404 na landing page
     * HTML do opencode.ai em vez de chegar na API. Não remover "normalizando" a URL.
     * <p>
     * Consequência: o discovery monta o path por sufixo ({@code baseUrl + "/models"}) —
     * nunca hardcodar {@code "/v1"} lá, senão o resultado vira {@code /v1/v1/models}.
     */
    public static final String OPENCODE_GO_BASE_URL = "https://opencode.ai/zen/go/v1";
    public static final String OPENCODE_ZEN_BASE_URL = "https://opencode.ai/zen/v1";

    // NOTE (INFRA-01, lista viva): o default do Zen é claude-sonnet-4-5 — modelo vivo no
    // catálogo remoto — e NÃO muse-spark-1.3-contributor. O muse-spark foi descontinuado
    // e está denylisted abaixo (padrão "muse-spark-*"); a SPEC que ainda o cita como
    // default está desatualizada e deve ser anotada pelo orchestrator.
    private static final Map<AiProvider, List<String>> MODELS = Map.of(
            AiProvider.OPENAI, List.of("gpt-4o-mini", "gpt-4o", "gpt-4", "gpt-3.5-turbo"),
            AiProvider.GEMINI, List.of("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash"),
            AiProvider.ANTHROPIC, List.of("claude-3-5-haiku-latest", "claude-3-5-sonnet-latest", "claude-3-opus-latest"),
            AiProvider.OLLAMA, List.of("llama3.2", "llama3.1", "mistral", "qwen2.5"),
            AiProvider.OPENAI_COMPATIBLE, List.of("mimo-v2.5"),
            AiProvider.OPENCODE_GO, List.of("mimo-v2.5"),
            AiProvider.OPENCODE_ZEN, List.of("claude-sonnet-4-5"));

    private static final Map<AiProvider, String> DEFAULTS = Map.of(
            AiProvider.OPENAI, "gpt-4o-mini",
            AiProvider.GEMINI, "gemini-2.0-flash",
            AiProvider.ANTHROPIC, "claude-3-5-haiku-latest",
            AiProvider.OLLAMA, "llama3.2",
            AiProvider.OPENAI_COMPATIBLE, "mimo-v2.5",
            AiProvider.OPENCODE_GO, "mimo-v2.5",
            AiProvider.OPENCODE_ZEN, "claude-sonnet-4-5");

    private static final List<String> DENYLIST_RESPONSES_PATTERNS = List.of("grok-*", "gpt-*-luna", "muse-spark-*");
    private static final List<String> DENYLIST_MESSAGES_PATTERNS = List.of("minimax-*", "qwen*");

    private static final List<Pattern> DENYLIST_PATTERNS = buildDenylist();

    private VendorCatalog() {
    }

    public static List<String> modelsFor(AiProvider provider) {
        return MODELS.getOrDefault(provider, List.of());
    }

    public static String defaultModelFor(AiProvider provider) {
        return DEFAULTS.get(provider);
    }

    public static String baseUrlFor(AiProvider provider) {
        if (provider == AiProvider.OPENCODE_GO) {
            return OPENCODE_GO_BASE_URL;
        }
        if (provider == AiProvider.OPENCODE_ZEN) {
            return OPENCODE_ZEN_BASE_URL;
        }
        return null;
    }

    public static boolean isDenylisted(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String candidate = model.trim();
        for (Pattern pattern : DENYLIST_PATTERNS) {
            if (pattern.matcher(candidate).matches()) {
                return true;
            }
        }
        return false;
    }

    public static boolean supports(AiProvider provider, String model) {
        if (provider == null || model == null || model.isBlank()) {
            return false;
        }
        if (provider == AiProvider.OPENAI_COMPATIBLE) {
            return true;
        }
        if (provider == AiProvider.OPENCODE_GO || provider == AiProvider.OPENCODE_ZEN) {
            return !isDenylisted(model);
        }
        return MODELS.getOrDefault(provider, List.of()).contains(model);
    }

    public static void validate(AiProvider provider, String model) {
        if (!supports(provider, model)) {
            throw new ModelNotSupportedForProviderException(
                    String.valueOf(model), String.valueOf(provider));
        }
    }

    private static List<Pattern> buildDenylist() {
        return java.util.stream.Stream.concat(
                        DENYLIST_RESPONSES_PATTERNS.stream(), DENYLIST_MESSAGES_PATTERNS.stream())
                .map(VendorCatalog::globToRegex)
                .map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE))
                .toList();
    }

    static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return regex.append("$").toString();
    }
}
