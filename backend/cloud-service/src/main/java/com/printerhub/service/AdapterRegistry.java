package com.printerhub.service;

import com.printerhub.core.adapter.PrinterAdapter;
import com.printerhub.core.entity.PrinterBrand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Discovers all PrinterAdapter implementations at startup and provides
 * a lookup by brand.
 *
 * How this works:
 * Spring injects *all* beans that implement PrinterAdapter into the constructor
 * as a List<PrinterAdapter>. We then index them by brand into a Map.
 *
 * This means adding a new adapter (e.g. PrusaAdapter) requires:
 *   1. Create the class annotated with @Component
 *   2. That's it — no changes needed here.
 *
 * This is the "open/closed principle" in practice: open for extension (new adapters),
 * closed for modification (AdapterRegistry never changes).
 */
@Slf4j
@Component
public class AdapterRegistry {

    private final Map<PrinterBrand, PrinterAdapter> adapters;

    public AdapterRegistry(List<PrinterAdapter> adapterList) {
        adapters = adapterList.stream()
                .collect(Collectors.toMap(
                        PrinterAdapter::getSupportedBrand,
                        Function.identity()
                ));
        log.info("Loaded {} printer adapter(s): {}", adapters.size(), adapters.keySet());
    }

    /**
     * @throws IllegalArgumentException if no adapter is registered for the given brand
     */
    public PrinterAdapter forBrand(PrinterBrand brand) {
        PrinterAdapter adapter = adapters.get(brand);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter registered for brand: " + brand);
        }
        return adapter;
    }

    public boolean supports(PrinterBrand brand) {
        return adapters.containsKey(brand);
    }
}
