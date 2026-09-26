package br.com.systec.hive.agent.api.domain;

import java.io.Serial;
import java.io.Serializable;

public class ToolDefinition implements Serializable {

    @Serial
    private static final long serialVersionUID = 4064659033333272943L;
    private String name;
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
