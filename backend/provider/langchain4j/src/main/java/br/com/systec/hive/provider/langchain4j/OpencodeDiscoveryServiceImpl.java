package br.com.systec.hive.provider.langchain4j;

import br.com.systec.hive.commons.impl.http.PinnedHttpClient;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.DiscoveredModels;
import br.com.systec.hive.provider.api.FilteredModel;
import br.com.systec.hive.provider.api.OpencodeDiscoveryService;
import br.com.systec.hive.provider.api.VendorCatalog;
import br.com.systec.hive.provider.api.exception.DiscoveryFailedException;
import br.com.systec.hive.provider.api.security.ProviderBaseUrlGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class OpencodeDiscoveryServiceImpl implements OpencodeDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(OpencodeDiscoveryServiceImpl.class);

    static final int TIMEOUT_MS = 5000;
    static final long MAX_BYTES = 512L * 1024L;
    static final int MAX_MODELS = 300;
    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    static final Pattern ID_SCHEMA = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<AiProvider, CachedDiscovery> cache = new ConcurrentHashMap<>();

    @Override
    public DiscoveredModels discover(AiProvider provider) {
        assertOpencode(provider);
        return discover(provider, VendorCatalog.baseUrlFor(provider));
    }

    DiscoveredModels discover(AiProvider provider, String baseUrl) {
        assertOpencode(provider);
        CachedDiscovery cached = cache.get(provider);
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return cached.payload();
        }
        DiscoveredModels fresh = fetch(provider, baseUrl);
        cache.put(provider, new CachedDiscovery(fresh, Instant.now().plus(CACHE_TTL)));
        return fresh;
    }

    @Override
    public void evict(AiProvider provider) {
        cache.remove(provider);
    }

    private DiscoveredModels fetch(AiProvider provider, String baseUrl) {
        try {
            ProviderBaseUrlGuard.validate(baseUrl);
            // Path por sufixo: o baseUrl vendor já termina em "/v1" (ver VendorCatalog).
            // Hardcodar "/v1" aqui produziria "/v1/v1/models".
            URI uri = URI.create(baseUrl.trim() + "/models");
            InetAddress pinned = selectPinnedAddress(uri.getHost(), provider);
            PinnedHttpClient.PinnedResponse response =
                    PinnedHttpClient.get(uri, pinned, TIMEOUT_MS, TIMEOUT_MS, MAX_BYTES);
            if (response.status() != 200) {
                log.warn("discovery non-200 provider={} status={}", provider, response.status());
                throw new DiscoveryFailedException(String.valueOf(provider));
            }
            return parse(provider, response.body());
        } catch (DiscoveryFailedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("discovery failed provider={}: {}", provider, e.getClass().getSimpleName());
            throw new DiscoveryFailedException(String.valueOf(provider), e);
        }
    }

    private DiscoveredModels parse(AiProvider provider, byte[] body) {
        List<String> models = new ArrayList<>();
        List<FilteredModel> filteredOut = new ArrayList<>();
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new DiscoveryFailedException(String.valueOf(provider), e);
        }
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new DiscoveryFailedException(String.valueOf(provider));
        }
        for (JsonNode item : data) {
            String id = item.path("id").asText(null);
            if (id == null || !ID_SCHEMA.matcher(id).matches()) {
                filteredOut.add(new FilteredModel(String.valueOf(id), "INVALID_ID"));
                continue;
            }
            if (VendorCatalog.isDenylisted(id)) {
                filteredOut.add(new FilteredModel(id, "DENYLISTED"));
                continue;
            }
            if (models.size() >= MAX_MODELS) {
                continue;
            }
            models.add(id);
        }
        return new DiscoveredModels(models, filteredOut, Instant.now());
    }

    private InetAddress selectPinnedAddress(String host, AiProvider provider) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()
                        || ProviderBaseUrlGuard.isCarrierGradeNat(address)) {
                    continue;
                }
                return address;
            }
        } catch (Exception e) {
            throw new DiscoveryFailedException(String.valueOf(provider), e);
        }
        throw new DiscoveryFailedException(String.valueOf(provider));
    }

    private static void assertOpencode(AiProvider provider) {
        if (provider != AiProvider.OPENCODE_GO && provider != AiProvider.OPENCODE_ZEN) {
            throw new IllegalArgumentException("discovery suportado apenas para OPENCODE_GO/OPENCODE_ZEN.");
        }
    }

    private record CachedDiscovery(DiscoveredModels payload, Instant expiresAt) {
    }
}
