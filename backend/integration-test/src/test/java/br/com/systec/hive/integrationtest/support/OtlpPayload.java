package br.com.systec.hive.integrationtest.support;

import com.google.protobuf.InvalidProtocolBufferException;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Decodifica o payload protobuf capturado — nenhuma asserção sobre JSON cru. */
public final class OtlpPayload {

    private OtlpPayload() {
    }

    public static ExportTraceServiceRequest decode(byte[] body) throws InvalidProtocolBufferException {
        return ExportTraceServiceRequest.parseFrom(body);
    }

    public static ExportTraceServiceRequest decodeOrNull(byte[] body) {
        try {
            return decode(body);
        } catch (InvalidProtocolBufferException e) {
            return null;
        }
    }

    public static List<Span> spans(ExportTraceServiceRequest request) {
        return request.getResourceSpansList().stream()
                .flatMap(resource -> resource.getScopeSpansList().stream())
                .flatMap(scope -> scope.getSpansList().stream())
                .toList();
    }

    public static Map<String, String> attributes(Span span) {
        Map<String, String> result = new HashMap<>();
        for (KeyValue entry : span.getAttributesList()) {
            result.put(entry.getKey(), entry.getValue().getStringValue());
        }
        return result;
    }
}
