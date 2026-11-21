package br.com.systec.hive.assistant.impl.ai;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.UUID;

/**
 * O AI Service do turno (ADR-020 §5.1) — a única fonte do prompt padrão em código.
 *
 * <p><h3>Precedência do system (regra congelada)</h3>
 * <p>{@code system} é o texto do {@code PromptTemplate} da sessão quando ele existe; quando a sessão
 * não tem template (ou ele está em branco), o chamador passa {@link #PROMPT}. A decisão é do
 * chamador, não deste proxy — assim um template escolhido nunca é sobrescrito pelo fallback.
 *
 * <p><h3>Por que {@code Result<String>} e não {@code String}</h3>
 * <p>O §5.1 do ADR desenha o retorno como {@code String}, mas a §5.2 e a SPEC RF-06 obrigam o
 * {@code TurnReply} a carregar {@code TokenUsage}/{@code FinishReason} medidos. {@code Result<T>}
 * é o tipo do LangChain4j 1.19.0 que devolve exatamente isso junto com o conteúdo
 * ({@code Result.tokenUsage()}, {@code Result.finishReason()}, {@code Result.finalResponse()});
 * ver fonte em /tmp/opencode/lc4j/main/dev/langchain4j/service/Result.java, tag 1.19.0, 2026-11-21.
 * Sem ele seria preciso um listener de modelo, que não é o "resultado final do proxy" que a RF-06 pede.
 *
 * <p><h3>Ajuste de API verificado (2026-11-21)</h3>
 * <p>O esboço do ADR colocava {@code @SystemMessage} no parâmetro {@code system}. No jar 1.19.0 a
 * anotação tem {@code @Target({TYPE, METHOD})} — parâmetro não compila. O texto vai no método e a
 * variável {@code {{system}}} é resolvida por {@code @V("system")} no parâmetro
 * ({@code DefaultAiServices.prepareSystemMessage}, linhas 606-613). A precedência sobre
 * {@code systemMessageProvider} também está confirmada ali (linhas 615-637).
 *
 * <p>{@code @UserMessage} também foi para o método pelo mesmo motivo: com a anotação no parâmetro o
 * LangChain4j usa o <em>valor do argumento como template</em>
 * ({@code findUserMessageTemplateFromAnnotatedParameter}, linhas 739-749) — um texto do usuário com
 * {@code {{...}}} seria interpretado como variável e um texto vazio estouraria
 * {@code IllegalConfigurationException}. No método o template é constante e o valor entra por
 * {@code @V}, sem interpolation involuntária.
 *
 * <p><h3>{@code @MemoryId}</h3>
 * <p>{@code sessionId} — nunca {@code turnId}. UUID tem {@code equals}/{@code hashCode} corretos,
 * então é elegível como chave de memória (MemoryId.java, tag 1.19.0, 2026-11-21).
 */
public interface AssistantDefault {

    /**
     * Texto exato do seed V6 (`V6__seed_default_prompt.sql:65-67`), preservado byte a byte.
     * A V8 remove as linhas do banco; esta constante é o fallback obrigatório em código.
     */
    String PROMPT = "Você é um assistente útil e respeitoso. Responda sempre em português brasileiro, "
            + "de forma clara, objetiva e correta. Se não tiver certeza, diga que não sabe "
            + "em vez de inventar. Mantenha o tom profissional e cordial.";

    /**
     * @param sessionId chave de memória por sessão — estável entre turnos, nunca o id do turno
     * @param system    template da sessão, ou {@link #PROMPT} quando a sessão não tem template
     * @param user      texto deste turno; o histórico vem da {@code ChatMemory} do provider
     */
    @SystemMessage("{{system}}")
    @UserMessage("{{user}}")
    Result<String> chat(@MemoryId UUID sessionId,
                        @V("system") String system,
                        @V("user") String user);
}
