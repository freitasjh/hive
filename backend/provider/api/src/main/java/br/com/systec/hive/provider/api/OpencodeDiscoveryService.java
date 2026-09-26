package br.com.systec.hive.provider.api;

public interface OpencodeDiscoveryService {

    DiscoveredModels discover(AiProvider provider);

    void evict(AiProvider provider);
}
