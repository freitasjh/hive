package br.com.systec.hive.commons.impl.entity;

import br.com.systec.hive.commons.impl.security.TenantListener;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.io.Serial;
import java.util.UUID;

@MappedSuperclass
@EntityListeners(TenantListener.class)
@FilterDef(name = "tenantFilter", parameters = {@ParamDef(name = "tenantId", type = UUID.class)})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class AbstractEntity extends BaseEntity{
    @Serial
    private static final long serialVersionUID = -861118043152753468L;

    /**
     * O contrato de nulabilidade de `tenant_id` NÃO é uniforme: ele varia legitimamente por tabela,
     * e por isso não pode ser fixado aqui.
     *
     * <p>Medido no banco em 2026-09-27, das 7 entidades que estendem esta classe:
     * <ul>
     *   <li>{@code NOT NULL} — as 4 tabelas do módulo `agent` ({@code chat_message},
     *       {@code chat_session}, {@code prompt_template}, {@code provider_credential}) e
     *       {@code tenant_llm_config} (módulo {@code tenant}).</li>
     *   <li><b>nullable</b> — {@code users} (módulo {@code identity}), declarada
     *       {@code tenant_id UUID} na V2. Hoje nenhuma linha tem NULL, mas o <i>DDL aceita</i>.</li>
     * </ul>
     *
     * <p>Por isso {@code nullable = true} aqui NÃO é o valor certo para 6 das 7 — é o único valor
     * que não mente para ninguém de forma catastrófica. Endurecer para {@code false} trocaria a
     * mentira pela outra: {@code UserEntity} passaria a afirmar {@code NOT NULL} sobre uma coluna
     * que o banco aceita como NULL, num campo de isolamento de tenant.
     *
     * <p>Com {@code ddl-auto=none} esta anotação é metadado inerte — o Hibernate não gera e não
     * valida schema. O que realmente isola tenant é o {@code @Filter} acima e o {@code NOT NULL} no
     * DDL das 5 tabelas que o exigem. A correção de fundo é <b>por entidade</b>, não no
     * compartilhado. Ver {@code .agents/rules/db-migration-flyway.md}.
     */
    @Column(name = "tenant_id", nullable = true, updatable = false)
    protected UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
