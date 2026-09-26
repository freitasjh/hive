package br.com.systec.hive.knowledge.impl.entity;

import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serial;
import java.util.UUID;

@Entity
@Table(name = "agent_knowledge_base")
public class AgentKbEntity extends AbstractEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "kb_id", nullable = false)
    private UUID kbId;

    public UUID getAgentId() {
        return agentId;
    }

    public void setAgentId(UUID agentId) {
        this.agentId = agentId;
    }

    public UUID getKbId() {
        return kbId;
    }

    public void setKbId(UUID kbId) {
        this.kbId = kbId;
    }
}
