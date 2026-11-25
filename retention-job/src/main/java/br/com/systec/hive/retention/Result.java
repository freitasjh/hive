package br.com.systec.hive.retention;

import java.util.List;

/** Resultado da execucao para auditoria; submitted conta envios, nao exclusoes confirmadas. */
record Result(int candidates, int submitted, List<String> traceIds, List<String> errors, int exit) {
}
