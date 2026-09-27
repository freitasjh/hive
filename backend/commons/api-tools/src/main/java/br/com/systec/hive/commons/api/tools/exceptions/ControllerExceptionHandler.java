package br.com.systec.hive.commons.api.tools.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.commons.api.exceptions.ObjectNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;

@RestControllerAdvice
public class ControllerExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ControllerExceptionHandler.class);

    private StandardError buildError(HttpStatus status, String detail, String instance, String code) {
        StandardError err = new StandardError();
        err.setType("about:blank");
        err.setTitle(status.getReasonPhrase());
        err.setStatus(status.value());
        err.setDetail(detail);
        err.setInstance(instance);
        err.setCode(code);
        err.setTimeStamp(System.currentTimeMillis());
        return err;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardError> errorServerException(Exception e, HttpServletRequest request) {
        log.error("Internal Server Error: ", e);
        StandardError err = buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro interno no servidor. Contate o suporte.", request.getRequestURI(), "INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<StandardError> baseException(BaseException e, HttpServletRequest request) {
        log.error("BaseException: ", e);
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        if (e.getHttpStatus() != null) {
            httpStatus = e.getHttpStatus();
        }
        String code = toSnakeUpper(e.getClass().getSimpleName().replace("Exception", ""));
        StandardError err = buildError(httpStatus, e.getMessage(), request.getRequestURI(), code);
        if (e.getDetailMessage() != null && !e.getDetailMessage().isBlank()) {
            err.setDetail(e.getDetailMessage());
        }
        return ResponseEntity.status(httpStatus).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    private String toSnakeUpper(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    @ExceptionHandler(ObjectNotFoundException.class)
    public ResponseEntity<StandardError> objectNotFoundException(ObjectNotFoundException e, HttpServletRequest request) {
        log.error("ObjectNotFoundException: ", e);
        String code = e.getClass().getSimpleName().toUpperCase().replace("EXCEPTION", "");
        if (code.isBlank()) code = "NOT_FOUND";
        StandardError err = buildError(HttpStatus.NOT_FOUND, e.getMessage(), request.getRequestURI(), code);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<StandardError> unreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException e, HttpServletRequest request) {
        // Body que não desserializa é entrada inválida, não falha do servidor. Sem este handler o
        // catch-all devolvia 500 e o cliente recebia "erro interno" ao mandar, por exemplo, um
        // kbIds com um id que não é UUID — um erro de digitação reportado como indisponibilidade.
        log.warn("Unreadable request body: {}", e.getMessage());
        StandardError err = buildError(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido ou em formato não suportado.",
                request.getRequestURI(), "MALFORMED_REQUEST");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<StandardError> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.error("Validation error: ", e);
        StringBuilder detailBuilder = new StringBuilder("Erro de validação nos campos enviados.");
        for (FieldError x : e.getBindingResult().getFieldErrors()) {
            detailBuilder.append(" ").append(x.getField()).append(": ").append(x.getDefaultMessage()).append(";");
        }
        ValidationError err = new ValidationError(HttpStatus.BAD_REQUEST.value(), "Erro de validação nos campos enviados.", System.currentTimeMillis());
        err.setType("about:blank");
        err.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        err.setStatus(HttpStatus.BAD_REQUEST.value());
        err.setDetail(detailBuilder.toString());
        err.setInstance(request.getRequestURI());
        err.setCode("VALIDATION_ERROR");
        for (FieldError x : e.getBindingResult().getFieldErrors()) {
            err.addError(x.getField(), x.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<StandardError> constraintViolation(jakarta.validation.ConstraintViolationException e, HttpServletRequest request) {
        log.error("Constraint violation: ", e);
        StringBuilder detail = new StringBuilder("Violação de regras de negócio/validação.");
        e.getConstraintViolations().forEach(v -> detail.append(" ").append(v.getPropertyPath()).append(": ").append(v.getMessage()).append(";"));
        ValidationError err = new ValidationError(HttpStatus.BAD_REQUEST.value(), "Violação de regras de negócio/validação.", System.currentTimeMillis());
        err.setType("about:blank");
        err.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        err.setStatus(HttpStatus.BAD_REQUEST.value());
        err.setDetail(detail.toString());
        err.setInstance(request.getRequestURI());
        err.setCode("VALIDATION_ERROR");
        e.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            err.addError(fieldName, violation.getMessage());
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<StandardError> illegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.error("Illegal argument: ", e);
        StandardError err = buildError(HttpStatus.BAD_REQUEST, e.getMessage(), request.getRequestURI(), "ILLEGAL_ARGUMENT");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<StandardError> maxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("Upload too large: {}", e.getMessage());
        StandardError err = buildError(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo excede o limite de 10MB.", request.getRequestURI(), "PAYLOAD_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<StandardError> authenticationException(AuthenticationException e, HttpServletRequest request) {
        log.warn("Authentication failed: {}", e.getMessage());
        StandardError err = buildError(HttpStatus.UNAUTHORIZED, "Credenciais inválidas", request.getRequestURI(), "INVALID_CREDENTIALS");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<StandardError> runtimeException(RuntimeException e, HttpServletRequest request) {
        log.error("RuntimeException: ", e);
        StandardError err = buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado. Contate o suporte.", request.getRequestURI(), "INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler({SQLException.class})
    public ResponseEntity<StandardError> databaseException(Exception e, HttpServletRequest request) {
        log.error("Database error occurred: ", e);
        StandardError err = buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro ao acessar o banco de dados.", request.getRequestURI(), "DATABASE_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<StandardError> accessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        log.warn("Access denied: {}", e.getMessage());
        StandardError err = buildError(HttpStatus.FORBIDDEN, e.getMessage(), request.getRequestURI(), "FORBIDDEN");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<StandardError> noRouteFound(Exception e, HttpServletRequest request) {
        log.warn("No route found: {}", e.getMessage());
        StandardError err = buildError(HttpStatus.NOT_FOUND, "Recurso não encontrado", request.getRequestURI(), "NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<StandardError> methodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("Method not supported: {}", e.getMessage());
        StandardError err = buildError(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido", request.getRequestURI(), "METHOD_NOT_ALLOWED");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<StandardError> dataIntegrityViolation(DataIntegrityViolationException e, HttpServletRequest request) {
        // A mensagem do driver carrega o schema inteiro (tabela, coluna, nome e definição da FK). Ela
        // fica no log e NUNCA no corpo da resposta: mapear constraint aqui é o que permite devolver
        // algo acionável em pt-BR sem revelar a estrutura do banco (OWASP A05). Cada ramo abaixo é
        // um literal escrito à mão — se um dia a tabela mudar de nome, a mensagem não muda.
        log.warn("Data integrity violation: {}", e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
        String causeMsg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : "";
        String lower = causeMsg != null ? causeMsg.toLowerCase() : "";
        String code = "DATA_CONFLICT";
        String detail = "Não foi possível concluir a operação porque o registro é referenciado por outros dados. Remova as dependências e tente novamente.";
        HttpStatus status = HttpStatus.CONFLICT;

        if (lower.contains("uniq_agent_default_per_tenant") || lower.contains("uniq_agent_default")) {
            code = "CONCURRENT_DEFAULT_UPDATE";
            detail = "Atualização concorrente do agente padrão. Tente novamente.";
        } else if (lower.contains("fk_agent_prompt_template") || lower.contains("fk_agent_prompt")) {
            code = "PROMPT_IN_USE";
            detail = "Prompt em uso por agente, não pode ser removido.";
        } else if (lower.contains("uniq_prompt_active_code_per_tenant") || lower.contains("uk_prompt_template_active_version")) {
            code = "PROMPT_CODE_DUPLICATE";
            detail = "Prompt code já existe no tenant.";
        } else if (lower.contains("uk_prompt_template_tenant_code_version") || lower.contains("uk_prompt_template_tenant_code")) {
            code = "PROMPT_CODE_DUPLICATE";
            detail = "Prompt code já existe no tenant.";
        } else if (lower.contains("uk_users_username") || lower.contains("username")) {
            detail = "Username já cadastrado.";
            code = "USERNAME_DUPLICATE";
        } else if (lower.contains("uk_users_email") || lower.contains("email")) {
            detail = "Email já cadastrado.";
            code = "EMAIL_DUPLICATE";
        } else if (lower.contains("uk_users_federal_id") || lower.contains("federal_id")) {
            detail = "Documento já cadastrado.";
            code = "DOCUMENT_DUPLICATE";
        } else if (lower.contains("sole_default_agent")) {
            code = "SOLE_DEFAULT_AGENT";
            detail = "Defina outro agente como padrão antes de desativar/excluir o único padrão.";
        } else if (isDuplicateKey(lower)) {
            // Último ramo de propósito: as constraints nomeadas acima continuam valendo primeiro, e
            // este só pega o que sobrou. Ele existe porque o default fala de "registro referenciado
            // por outros dados", o que é FALSO para violação de chave única — o registro em
            // conflito é o próprio registro novo, não uma dependência. Sem este ramo, criar dois
            // registros com o mesmo valor_UNIQUE respondia "remova as dependências", e não existe
            // dependência nenhuma para remover: o operador ficava sem ação possível.
            //
            // Alcançável hoje (2026-09-27): uk_provider_credential_tenant_provider, por race de
            // check-then-act — o serviço verifica se a credencial existe e o commit de duas requisições
            // simultâneas ainda pode colidir, porque repository.save() não faz flush e a violação
            // só acontece no commit. É o único caminho que sobra para este ramo depois que as
            // constraints de slug de base de conhecimento e de sha de documento saíram com o módulo
            // knowledge (Batch 1, 2026-09-27); as tabelas continuam no banco, mas nada as popula.
            code = "ALREADY_EXISTS";
            detail = "Já existe um registro com estes dados. Revise os valores informados e tente novamente.";
        }

        StandardError err = buildError(status, detail, request.getRequestURI(), code);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(err);
    }

    /**
     * Violação de chave única — e não de chave estrangeira.
     *
     * <p>O driver do PostgreSQL abre a mensagem com {@code duplicate key value violates unique
     * constraint}; a forma {@code violates unique constraint} sem o prefixo também é aceita, para o
     * caso de um invólucro ou driver reportar só a cláusula da constraint.
     */
    private boolean isDuplicateKey(String lower) {
        return lower.contains("duplicate key value") || lower.contains("unique constraint");
    }
}
