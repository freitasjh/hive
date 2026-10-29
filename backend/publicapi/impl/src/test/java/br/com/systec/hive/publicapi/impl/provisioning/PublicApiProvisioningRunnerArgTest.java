package br.com.systec.hive.publicapi.impl.provisioning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicApiProvisioningRunnerArgTest {

    @Test
    void when_argPresent_thenReturnsValue() {
        assertThat(PublicApiProvisioningRunner.arg(new String[]{"--label=x"}, "--label=", "def"))
                .isEqualTo("x");
    }

    @Test
    void when_argAbsent_thenReturnsDefault() {
        assertThat(PublicApiProvisioningRunner.arg(new String[]{"--other"}, "--label=", "def"))
                .isEqualTo("def");
    }

    @Test
    void when_argWithPrefixValue_thenParses() {
        assertThat(PublicApiProvisioningRunner.arg(new String[]{"--tenant-id=abc"}, "--tenant-id=", "def"))
                .isEqualTo("abc");
    }
}