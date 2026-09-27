package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * O provider respondeu 200 mas sem texto utilizável: {@code content} veio {@code null} ou em branco.
 *
 * <p>Não é erro de configuração nem indisponibilidade do modelo — é o modelo consumindo todo o
 * orçamento de tokens em raciocínio e não sobrando nada para a resposta. Medido no upstream com
 * {@code mimo-v2.5}: {@code max_tokens=32} devolve {@code finish_reason=length},
 * {@code content=null} e 156 chars de {@code reasoning}. Com 64/128/256 o mesmo modelo já devolve
 * texto (a variável é o modelo, e por isso a guarda cobre {@code null} <b>e</b> branco).
 *
 * <p>Sem esta exceção o {@code null} chegava ao {@code save} e virava
 * {@code DataIntegrityViolationException} (409) em {@code chat_message.content NOT NULL} — um erro
 * de integridade que não diz nada ao usuário e ainda esconde a causa real do problema.
 *
 * <p>O nome da classe não é livre: {@code ControllerExceptionHandler} deriva o {@code code} do
 * SimpleName em SNAKE_UPPER, e o contrato exigido é {@code MODEL_RETURNED_EMPTY}.
 * {@code ModelEmptyResponseException} produziria {@code MODEL_EMPTY_RESPONSE}.
 */
public class ModelReturnedEmptyException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ModelReturnedEmptyException(String model) {
        super("O modelo " + model + " não devolveu texto (limite de tokens atingido sem conteúdo). "
                        + "Aumente o limite de tokens do prompt ou encurte a pergunta.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
