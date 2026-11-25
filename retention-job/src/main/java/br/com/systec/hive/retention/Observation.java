package br.com.systec.hive.retention;

/** Linha minima da Observations API v2 (field groups core,basic). */
public record Observation(String traceId, String startTime, boolean isRootObservation) {
}
