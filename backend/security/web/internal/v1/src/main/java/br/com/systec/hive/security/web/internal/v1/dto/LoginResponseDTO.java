package br.com.systec.hive.security.web.internal.v1.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public record LoginResponseDTO(
    UUID userId,
    String accessToken,
    String type
    ) {}