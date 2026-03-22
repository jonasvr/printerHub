package com.printerhub.service;

import com.printerhub.core.adapter.PrinterAdapter;
import com.printerhub.core.entity.Printer;
import com.printerhub.core.entity.PrinterBrand;
import com.printerhub.core.entity.PrinterState;
import com.printerhub.core.repository.PrinterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrinterServiceTest {

    @Mock PrinterRepository printerRepository;
    @Mock AdapterRegistry adapterRegistry;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock PrinterAdapter printerAdapter;

    @InjectMocks PrinterService printerService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Printer makePrinter(UUID id, String name, PrinterBrand brand) {
        Printer p = new Printer();
        p.setId(id);
        p.setName(name);
        p.setBrand(brand);
        p.setActive(true);
        p.setState(PrinterState.IDLE);
        p.setAccessCode("secret");
        return p;
    }

    // ── registerPrinter ───────────────────────────────────────────────────────

    @Test
    void registerPrinter_savesAndConnects() {
        UUID id = UUID.randomUUID();
        Printer printer = makePrinter(id, "X1C", PrinterBrand.BAMBU);
        when(printerRepository.save(any())).thenReturn(printer);
        when(adapterRegistry.forBrand(PrinterBrand.BAMBU)).thenReturn(printerAdapter);

        printerService.registerPrinter(printer);

        verify(printerRepository).save(printer);
        verify(adapterRegistry).forBrand(PrinterBrand.BAMBU);
        verify(printerAdapter).connect(printer);
    }

    // ── updatePrinter ─────────────────────────────────────────────────────────

    @Test
    void updatePrinter_notFound_throwsIllegalArgumentException() {
        UUID id = UUID.randomUUID();
        when(printerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> printerService.updatePrinter(id, new Printer()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatePrinter_patchesFieldsAndReconnects() {
        UUID id = UUID.randomUUID();
        Printer existing = makePrinter(id, "OldName", PrinterBrand.BAMBU);
        Printer patch = makePrinter(id, "NewName", PrinterBrand.BAMBU);
        patch.setModel("P1S");
        patch.setIpAddress("192.168.1.50");

        when(printerRepository.findById(id)).thenReturn(Optional.of(existing));
        when(printerRepository.save(any())).thenReturn(existing);
        when(adapterRegistry.forBrand(PrinterBrand.BAMBU)).thenReturn(printerAdapter);

        printerService.updatePrinter(id, patch);

        verify(printerAdapter).disconnect(id);
        verify(printerAdapter).connect(existing);
        assertThat(existing.getName()).isEqualTo("NewName");
        assertThat(existing.getModel()).isEqualTo("P1S");
        assertThat(existing.getIpAddress()).isEqualTo("192.168.1.50");
    }

    @Test
    void updatePrinter_blankAccessCode_doesNotOverwriteExistingCode() {
        UUID id = UUID.randomUUID();
        Printer existing = makePrinter(id, "X1C", PrinterBrand.BAMBU);
        existing.setAccessCode("original-secret");

        Printer patch = makePrinter(id, "X1C", PrinterBrand.BAMBU);
        patch.setAccessCode("  ");  // blank — should not overwrite

        when(printerRepository.findById(id)).thenReturn(Optional.of(existing));
        when(printerRepository.save(any())).thenReturn(existing);
        when(adapterRegistry.forBrand(PrinterBrand.BAMBU)).thenReturn(printerAdapter);

        printerService.updatePrinter(id, patch);

        assertThat(existing.getAccessCode()).isEqualTo("original-secret");
    }

    // ── deregisterPrinter ─────────────────────────────────────────────────────

    @Test
    void deregisterPrinter_setsActiveFalseAndDisconnects() {
        UUID id = UUID.randomUUID();
        Printer printer = makePrinter(id, "X1C", PrinterBrand.BAMBU);
        when(printerRepository.findById(id)).thenReturn(Optional.of(printer));
        when(adapterRegistry.forBrand(PrinterBrand.BAMBU)).thenReturn(printerAdapter);

        printerService.deregisterPrinter(id);

        verify(printerAdapter).disconnect(id);
        assertThat(printer.isActive()).isFalse();
        verify(printerRepository).save(printer);
    }

    @Test
    void deregisterPrinter_notFound_throwsIllegalArgumentException() {
        UUID id = UUID.randomUUID();
        when(printerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> printerService.deregisterPrinter(id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── getAllActivePrinters / getPrinter ─────────────────────────────────────

    @Test
    void getAllActivePrinters_delegatesToRepository() {
        UUID id = UUID.randomUUID();
        List<Printer> expected = List.of(makePrinter(id, "X1C", PrinterBrand.BAMBU));
        when(printerRepository.findAllByActiveTrue()).thenReturn(expected);

        assertThat(printerService.getAllActivePrinters()).isEqualTo(expected);
    }

    @Test
    void getPrinter_found_returnsPrinter() {
        UUID id = UUID.randomUUID();
        Printer printer = makePrinter(id, "X1C", PrinterBrand.BAMBU);
        when(printerRepository.findById(id)).thenReturn(Optional.of(printer));

        assertThat(printerService.getPrinter(id)).isSameAs(printer);
    }

    @Test
    void getPrinter_notFound_throwsIllegalArgumentException() {
        UUID id = UUID.randomUUID();
        when(printerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> printerService.getPrinter(id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Control commands ──────────────────────────────────────────────────────

    @Test
    void pause_delegatesToAdapter() {
        UUID id = UUID.randomUUID();
        Printer printer = makePrinter(id, "X1C", PrinterBrand.BAMBU);
        when(printerRepository.findById(id)).thenReturn(Optional.of(printer));
        when(adapterRegistry.forBrand(PrinterBrand.BAMBU)).thenReturn(printerAdapter);

        printerService.pause(id);

        verify(printerAdapter).pause(id);
    }

    @Test
    void resume_delegatesToAdapter() {
        UUID id = UUID.randomUUID();
        Printer printer = makePrinter(id, "X1C", PrinterBrand.BAMBU);
        when(printerRepository.findById(id)).thenReturn(Optional.of(printer));
        when(adapterRegistry.forBrand(PrinterBrand.BAMBU)).thenReturn(printerAdapter);

        printerService.resume(id);

        verify(printerAdapter).resume(id);
    }

    @Test
    void cancel_delegatesToAdapter() {
        UUID id = UUID.randomUUID();
        Printer printer = makePrinter(id, "X1C", PrinterBrand.BAMBU);
        when(printerRepository.findById(id)).thenReturn(Optional.of(printer));
        when(adapterRegistry.forBrand(PrinterBrand.BAMBU)).thenReturn(printerAdapter);

        printerService.cancel(id);

        verify(printerAdapter).cancel(id);
    }
}
