package com.printerhub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printerhub.core.adapter.MqttLogEntry;
import com.printerhub.core.entity.Printer;
import com.printerhub.core.entity.PrinterBrand;
import com.printerhub.core.entity.PrinterState;
import com.printerhub.service.PrinterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @SpringBootTest + @AutoConfigureMockMvc instead of @WebMvcTest because
// @EnableJpaRepositories on the main app class triggers full JPA bootstrap
// even in the web slice, which @WebMvcTest cannot suppress without
// restructuring the application config. H2 (application-test.yml) is used
// as the datasource; @MockBean PrinterService prevents any MQTT connections.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrinterControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PrinterService printerService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Printer makePrinter(UUID id) {
        Printer p = new Printer();
        p.setId(id);
        p.setName("X1C");
        p.setBrand(PrinterBrand.BAMBU);
        p.setState(PrinterState.IDLE);
        p.setActive(true);
        return p;
    }

    // ── GET /api/v1/printers ──────────────────────────────────────────────────

    @Test
    void listPrinters_returns200WithArray() throws Exception {
        UUID id = UUID.randomUUID();
        when(printerService.getAllActivePrinters()).thenReturn(List.of(makePrinter(id)));

        mockMvc.perform(get("/api/v1/printers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("X1C"));
    }

    // ── GET /api/v1/printers/{id} ─────────────────────────────────────────────

    @Test
    void getPrinter_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(printerService.getPrinter(id)).thenReturn(makePrinter(id));

        mockMvc.perform(get("/api/v1/printers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("X1C"));
    }

    @Test
    void getPrinter_notFound_propagatesException() {
        // TODO: add @ControllerAdvice to map IllegalArgumentException → 404
        // Without a @ControllerAdvice, Spring rethrows unhandled exceptions in
        // @SpringBootTest MockMvc rather than mapping them to an HTTP status.
        UUID id = UUID.randomUUID();
        when(printerService.getPrinter(id))
                .thenThrow(new IllegalArgumentException("Printer not found: " + id));

        assertThatThrownBy(() -> mockMvc.perform(get("/api/v1/printers/{id}", id)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Printer not found");
    }

    // ── POST /api/v1/printers ─────────────────────────────────────────────────

    @Test
    void addPrinter_returns201WithBody() throws Exception {
        UUID id = UUID.randomUUID();
        Printer incoming = makePrinter(null);
        when(printerService.registerPrinter(any())).thenReturn(makePrinter(id));

        mockMvc.perform(post("/api/v1/printers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incoming)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    // ── PATCH /api/v1/printers/{id} ───────────────────────────────────────────

    @Test
    void updatePrinter_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        Printer patch = makePrinter(id);
        when(printerService.updatePrinter(eq(id), any())).thenReturn(patch);

        mockMvc.perform(patch("/api/v1/printers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("X1C"));
    }

    // ── DELETE /api/v1/printers/{id} ──────────────────────────────────────────

    @Test
    void removePrinter_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/printers/{id}", id))
                .andExpect(status().isNoContent());

        verify(printerService).deregisterPrinter(id);
    }

    // ── POST /api/v1/printers/{id}/pause ─────────────────────────────────────

    @Test
    void pause_returns202() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/printers/{id}/pause", id))
                .andExpect(status().isAccepted());

        verify(printerService).pause(id);
    }

    // ── POST /api/v1/printers/{id}/resume ────────────────────────────────────

    @Test
    void resume_returns202() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/printers/{id}/resume", id))
                .andExpect(status().isAccepted());

        verify(printerService).resume(id);
    }

    // ── POST /api/v1/printers/{id}/cancel ────────────────────────────────────

    @Test
    void cancel_returns202() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/printers/{id}/cancel", id))
                .andExpect(status().isAccepted());

        verify(printerService).cancel(id);
    }

    // ── GET /api/v1/printers/{id}/mqtt-logs ──────────────────────────────────

    @Test
    void getMqttLogs_returns200WithList() throws Exception {
        UUID id = UUID.randomUUID();
        MqttLogEntry entry = new MqttLogEntry(id, Instant.now(), "{\"print\":{}}");
        when(printerService.getMqttLogs(id)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/printers/{id}/mqtt-logs", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
