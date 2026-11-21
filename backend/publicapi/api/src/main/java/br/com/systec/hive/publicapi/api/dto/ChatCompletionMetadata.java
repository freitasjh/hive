package br.com.systec.hive.publicapi.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ChatCompletionMetadata", description = "Hive extension envelope; the tenant payload lives under metadata.hive.")
public class ChatCompletionMetadata {

    private HiveMetadata hive;

    public ChatCompletionMetadata() {
    }

    public ChatCompletionMetadata(HiveMetadata hive) {
        this.hive = hive;
    }

    public HiveMetadata getHive() { return hive; }
    public void setHive(HiveMetadata hive) { this.hive = hive; }
}
