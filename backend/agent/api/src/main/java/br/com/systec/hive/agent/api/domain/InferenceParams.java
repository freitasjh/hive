package br.com.systec.hive.agent.api.domain;

import java.io.Serial;
import java.io.Serializable;

public class InferenceParams implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Double temperature;
    private Double topP;
    private Integer maxTokens;

    public InferenceParams() {
    }

    public InferenceParams(Double temperature, Double topP, Integer maxTokens) {
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
}
