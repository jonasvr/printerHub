package com.printerhub.controller;

import com.printerhub.core.adapter.MqttLogEntry;
import com.printerhub.core.entity.Printer;
import com.printerhub.service.PrinterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/printers")
@CrossOrigin(origins = "http://localhost:4200",
             methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PATCH,
                        RequestMethod.DELETE, RequestMethod.OPTIONS})
@RequiredArgsConstructor
@Tag(name = "Printers", description = "Register, query and control 3D printers")
public class PrinterController {

    private final PrinterService printerService;

    @Operation(summary = "List all active printers")
    @GetMapping
    public List<Printer> listPrinters() {
        return printerService.getAllActivePrinters();
    }

    @Operation(summary = "Get a single printer by ID")
    @ApiResponse(responseCode = "200", description = "Printer found")
    @ApiResponse(responseCode = "404", description = "Printer not found")
    @GetMapping("/{id}")
    public ResponseEntity<Printer> getPrinter(@PathVariable UUID id) {
        return ResponseEntity.ok(printerService.getPrinter(id));
    }

    @Operation(summary = "Register a new printer",
               description = "Persists the printer and opens an adapter connection. Returns 201 with the saved entity including its generated UUID.")
    @ApiResponse(responseCode = "201", description = "Printer registered")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Printer addPrinter(@RequestBody Printer printer) {
        return printerService.registerPrinter(printer);
    }

    @Operation(summary = "Update a printer's details and reconnect its adapter")
    @ApiResponse(responseCode = "200", description = "Printer updated")
    @PatchMapping("/{id}")
    public Printer updatePrinter(@PathVariable UUID id, @RequestBody Printer printer) {
        return printerService.updatePrinter(id, printer);
    }

    @Operation(summary = "Remove a printer", description = "Soft-delete: sets active=false, disconnects the adapter.")
    @ApiResponse(responseCode = "204", description = "Printer removed")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePrinter(@PathVariable UUID id) {
        printerService.deregisterPrinter(id);
    }

    @Operation(summary = "Pause the current print")
    @PostMapping("/{id}/pause")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void pause(@PathVariable UUID id) {
        printerService.pause(id);
    }

    @Operation(summary = "Resume a paused print")
    @PostMapping("/{id}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resume(@PathVariable UUID id) {
        printerService.resume(id);
    }

    @Operation(summary = "Cancel the current print")
    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void cancel(@PathVariable UUID id) {
        printerService.cancel(id);
    }

    @Operation(summary = "Recent MQTT log entries for a printer (last 100, newest first)")
    @GetMapping("/{id}/mqtt-logs")
    public List<MqttLogEntry> getMqttLogs(@PathVariable UUID id) {
        return printerService.getMqttLogs(id);
    }
}
