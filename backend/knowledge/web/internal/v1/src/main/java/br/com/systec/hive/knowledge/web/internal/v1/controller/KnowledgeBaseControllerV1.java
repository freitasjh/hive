package br.com.systec.hive.knowledge.web.internal.v1.controller;

import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.api.tools.security.SecurityConstants;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.domain.KbDocument;
import br.com.systec.hive.knowledge.api.domain.KnowledgeBase;
import br.com.systec.hive.knowledge.api.service.KbDocumentService;
import br.com.systec.hive.knowledge.api.service.KnowledgeBaseService;
import br.com.systec.hive.knowledge.web.internal.v1.dto.KbChunkDTO;
import br.com.systec.hive.knowledge.web.internal.v1.dto.KbDocumentDTO;
import br.com.systec.hive.knowledge.web.internal.v1.dto.KbSearchDTO;
import br.com.systec.hive.knowledge.web.internal.v1.dto.KbUrlDTO;
import br.com.systec.hive.knowledge.web.internal.v1.dto.KnowledgeBaseCreateDTO;
import br.com.systec.hive.knowledge.web.internal.v1.dto.KnowledgeBaseDTO;
import br.com.systec.hive.knowledge.web.internal.v1.dto.KnowledgeBaseUpdateDTO;
import br.com.systec.hive.knowledge.web.internal.v1.mapper.KnowledgeBaseDTOMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(RestPath.V1 + "/knowledge-bases")
@Tag(name = "Hive Knowledge - Bases", description = "Gestão de bases de conhecimento (RAG)")
public class KnowledgeBaseControllerV1 {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KbDocumentService documentService;
    private final KnowledgeBaseDTOMapper mapper;

    public KnowledgeBaseControllerV1(KnowledgeBaseService knowledgeBaseService,
                                     KbDocumentService documentService,
                                     KnowledgeBaseDTOMapper mapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentService = documentService;
        this.mapper = mapper;
    }

    private UUID currentTenantId() {
        return TenantContext.getCurrentTenant();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Cria uma base de conhecimento")
    @HasPermission(SecurityConstants.KNOWLEDGE_MANAGE)
    public ResponseEntity<KnowledgeBaseDTO> create(@RequestBody @Valid KnowledgeBaseCreateDTO payload) {
        KnowledgeBase domain = mapper.toDomain(payload);
        domain.setTenantId(currentTenantId());
        KnowledgeBase saved = knowledgeBaseService.create(domain);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(saved));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lista bases de conhecimento paginado")
    @HasPermission(anyOf = {SecurityConstants.KNOWLEDGE_READ, SecurityConstants.KNOWLEDGE_MANAGE})
    public ResponseEntity<Page<KnowledgeBaseDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {
        if (size > 50) {
            throw new IllegalArgumentException("size must be <= 50");
        }
        if (size <= 0) {
            size = 20;
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateAt"));
        Page<KnowledgeBase> result = knowledgeBaseService.list(currentTenantId(), pageable, q);
        return ResponseEntity.ok(result.map(mapper::toDto));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Busca base por id")
    @HasPermission(anyOf = {SecurityConstants.KNOWLEDGE_READ, SecurityConstants.KNOWLEDGE_MANAGE})
    public ResponseEntity<KnowledgeBaseDTO> get(@PathVariable UUID id) {
        KnowledgeBase kb = knowledgeBaseService.findById(id, currentTenantId());
        return RestControllerTool.of().buildSuccessResponse(mapper.toDto(kb));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Atualiza base de conhecimento")
    @HasPermission(SecurityConstants.KNOWLEDGE_MANAGE)
    public ResponseEntity<KnowledgeBaseDTO> update(@PathVariable UUID id,
                                                  @RequestBody @Valid KnowledgeBaseUpdateDTO payload) {
        KnowledgeBase domain = mapper.toDomain(payload);
        domain.setId(id);
        domain.setTenantId(currentTenantId());
        return RestControllerTool.of().buildSuccessResponse(mapper.toDto(knowledgeBaseService.update(domain)));
    }

    @DeleteMapping("/{id}")
    @Operation(description = "Remove base; 409 se vinculada (use ?force=true)")
    @HasPermission(SecurityConstants.KNOWLEDGE_MANAGE)
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestParam(defaultValue = "false") boolean force) {
        knowledgeBaseService.delete(id, currentTenantId(), force);
        return RestControllerTool.of().buildSuccessResponseNoContent();
    }

    @PostMapping(value = "/{id}/documents:upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Upload de documento (PDF/TXT/MD, max 10MB) — snapshot único")
    @HasPermission(SecurityConstants.KNOWLEDGE_MANAGE)
    public ResponseEntity<KbDocumentDTO> upload(@PathVariable UUID id,
                                                @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        try {
            KbDocument doc = documentService.upload(id, currentTenantId(),
                    file.getOriginalFilename(), file.getContentType(), file.getInputStream());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapper.toDto(doc));
        } catch (br.com.systec.hive.commons.api.exceptions.BaseException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("upload failed", e);
        }
    }

    @PostMapping(value = "/{id}/documents:url", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Ingere URL pública (snapshot único, sem recrawl)")
    @HasPermission(SecurityConstants.KNOWLEDGE_MANAGE)
    public ResponseEntity<KbDocumentDTO> addUrl(@PathVariable UUID id,
                                                @RequestBody @Valid KbUrlDTO payload) {
        KbDocument doc = knowledgeBaseService.addUrlDocument(id, currentTenantId(), payload.getUrl());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapper.toDto(doc));
    }

    @GetMapping(value = "/{id}/documents", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lista documentos da base com status")
    @HasPermission(anyOf = {SecurityConstants.KNOWLEDGE_READ, SecurityConstants.KNOWLEDGE_MANAGE})
    public ResponseEntity<List<KbDocumentDTO>> listDocuments(@PathVariable UUID id) {
        List<KbDocumentDTO> docs = documentService.listByKb(id, currentTenantId()).stream()
                .map(mapper::toDto).toList();
        return RestControllerTool.of().buildSuccessResponse(docs);
    }

    @DeleteMapping("/{kbId}/documents/{docId}")
    @Operation(description = "Remove documento e seus embeddings")
    @HasPermission(SecurityConstants.KNOWLEDGE_MANAGE)
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID kbId, @PathVariable UUID docId) {
        KbDocument doc = documentService.findById(docId, currentTenantId());
        if (!doc.getKbId().equals(kbId)) {
            throw new IllegalArgumentException("document does not belong to knowledge base");
        }
        documentService.delete(docId, currentTenantId());
        return RestControllerTool.of().buildSuccessResponseNoContent();
    }

    @PostMapping(value = "/{id}/reindex", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Reindexa base (idempotente)")
    @HasPermission(SecurityConstants.KNOWLEDGE_MANAGE)
    public ResponseEntity<Void> reindex(@PathVariable UUID id) {
        knowledgeBaseService.reindex(id, currentTenantId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping(value = "/{id}/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Playground: busca chunks relevantes")
    @HasPermission(anyOf = {SecurityConstants.KNOWLEDGE_READ, SecurityConstants.KNOWLEDGE_MANAGE})
    public ResponseEntity<List<KbChunkDTO>> search(@PathVariable UUID id,
                                                   @RequestBody @Valid KbSearchDTO payload) {
        int topK = payload.getTopK() == null ? 5 : payload.getTopK();
        List<KbChunk> chunks = knowledgeBaseService.search(id, currentTenantId(), payload.getQuery(), topK);
        return RestControllerTool.of().buildSuccessResponse(KbChunkDTO.ofList(chunks));
    }
}
