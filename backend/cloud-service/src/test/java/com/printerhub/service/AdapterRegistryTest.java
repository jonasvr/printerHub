package com.printerhub.service;

import com.printerhub.core.adapter.PrinterAdapter;
import com.printerhub.core.entity.PrinterBrand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdapterRegistryTest {

    private PrinterAdapter mockAdapter(PrinterBrand brand) {
        PrinterAdapter adapter = mock(PrinterAdapter.class);
        when(adapter.getSupportedBrand()).thenReturn(brand);
        return adapter;
    }

    @Test
    void forBrand_registeredBrand_returnsAdapter() {
        PrinterAdapter bambu = mockAdapter(PrinterBrand.BAMBU);
        AdapterRegistry registry = new AdapterRegistry(List.of(bambu));

        assertThat(registry.forBrand(PrinterBrand.BAMBU)).isSameAs(bambu);
    }

    @Test
    void forBrand_unregisteredBrand_throwsIllegalArgumentException() {
        PrinterAdapter bambu = mockAdapter(PrinterBrand.BAMBU);
        AdapterRegistry registry = new AdapterRegistry(List.of(bambu));

        assertThatThrownBy(() -> registry.forBrand(PrinterBrand.PRUSA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRUSA");
    }

    @Test
    void supports_registeredBrand_returnsTrue() {
        AdapterRegistry registry = new AdapterRegistry(List.of(mockAdapter(PrinterBrand.BAMBU)));

        assertThat(registry.supports(PrinterBrand.BAMBU)).isTrue();
    }

    @Test
    void supports_unregisteredBrand_returnsFalse() {
        AdapterRegistry registry = new AdapterRegistry(List.of(mockAdapter(PrinterBrand.BAMBU)));

        assertThat(registry.supports(PrinterBrand.PRUSA)).isFalse();
    }

    @Test
    void constructor_multipleAdapters_indexedCorrectly() {
        PrinterAdapter bambu = mockAdapter(PrinterBrand.BAMBU);
        PrinterAdapter prusa = mockAdapter(PrinterBrand.PRUSA);
        AdapterRegistry registry = new AdapterRegistry(List.of(bambu, prusa));

        assertThat(registry.supports(PrinterBrand.BAMBU)).isTrue();
        assertThat(registry.supports(PrinterBrand.PRUSA)).isTrue();
        assertThat(registry.forBrand(PrinterBrand.BAMBU)).isSameAs(bambu);
        assertThat(registry.forBrand(PrinterBrand.PRUSA)).isSameAs(prusa);
    }
}
