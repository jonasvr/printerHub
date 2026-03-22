package com.printerhub.adapter.bambu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printerhub.core.adapter.AmsStatus;
import com.printerhub.core.adapter.HmsAlert;
import com.printerhub.core.adapter.PrinterStatusUpdate;
import com.printerhub.core.entity.PrinterState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BambuStatusParseTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static final String PRINTING_FULL = """
            {
              "print": {
                "gcode_state": "RUNNING",
                "mc_percent": 47.5,
                "subtask_name": "benchy.3mf",
                "bed_temper": 62,
                "bed_target_temper": 65,
                "nozzle_temper": 218,
                "nozzle_target_temper": 220,
                "mc_remaining_time": 83,
                "chamber_temper": 31,
                "layer_num": 42,
                "total_layer_num": 180,
                "spd_mag": 100,
                "hms": [],
                "ams": {
                  "tray_now": "2",
                  "ams": [
                    {
                      "id": "0",
                      "tray": [
                        { "id": "0", "tray_type": "PLA",  "tray_sub_brands": "Bambu",   "tray_color": "56B7E6FF" },
                        { "id": "1", "tray_type": "PETG", "tray_sub_brands": "",        "tray_color": "000000FF" },
                        { "id": "2", "tray_type": "ABS",  "tray_sub_brands": "Generic", "tray_color": "FF5733FF" },
                        { "id": "3", "tray_type": "",     "tray_sub_brands": "",        "tray_color": "000000FF" }
                      ]
                    }
                  ]
                }
              }
            }
            """;

    private static final String IDLE = """
            { "print": { "gcode_state": "FINISH", "mc_percent": 100, "subtask_name": "" } }
            """;

    private static final String PAUSED = """
            { "print": { "gcode_state": "PAUSE", "mc_percent": 55.0, "subtask_name": "vase.3mf" } }
            """;

    private static final String FAILED = """
            { "print": { "gcode_state": "FAILED" } }
            """;

    private static final String HMS_ALERTS = """
            {
              "print": {
                "gcode_state": "RUNNING",
                "mc_percent": 20.0,
                "hms": [
                  { "attr": 50462720, "code": 65543 },
                  { "attr": 33686016, "code": 12289 }
                ]
              }
            }
            """;

    private static final String NO_AMS = """
            { "print": { "gcode_state": "RUNNING", "mc_percent": 10.0 } }
            """;

    private static final String UNKNOWN_STATE = """
            { "print": { "gcode_state": "PREPARE", "mc_percent": 0 } }
            """;

    // ── Setup ─────────────────────────────────────────────────────────────────

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BambuAdapter adapter;
    private final UUID printerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BambuMqttProperties props = mock(BambuMqttProperties.class);
        when(props.getMqttPort()).thenReturn(8883);
        when(props.isSkipTlsVerify()).thenReturn(true);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        adapter = new BambuAdapter(props, objectMapper, publisher);
    }

    // ── parseBambuPayload ─────────────────────────────────────────────────────

    @Test
    void printingState_mapsToRunning() throws Exception {
        JsonNode root = objectMapper.readTree(PRINTING_FULL);
        PrinterStatusUpdate update = adapter.parseBambuPayload(printerId, root);

        assertThat(update.state()).isEqualTo(PrinterState.PRINTING);
        assertThat(update.progressPercent()).isEqualTo(47.5);
        assertThat(update.currentFile()).isEqualTo("benchy.3mf");
        assertThat(update.bedTempActual()).isEqualTo(62);
        assertThat(update.nozzleTempActual()).isEqualTo(218);
        assertThat(update.remainingMinutes()).isEqualTo(83);
        assertThat(update.mqttConnected()).isTrue();
        assertThat(update.connectionError()).isNull();
    }

    @Test
    void temperatures_parsedCorrectly() throws Exception {
        JsonNode root = objectMapper.readTree(PRINTING_FULL);
        PrinterStatusUpdate update = adapter.parseBambuPayload(printerId, root);

        assertThat(update.bedTempTarget()).isEqualTo(65);
        assertThat(update.nozzleTempTarget()).isEqualTo(220);
        assertThat(update.chamberTempActual()).isEqualTo(31);
    }

    @Test
    void layerAndSpeed_parsedCorrectly() throws Exception {
        JsonNode root = objectMapper.readTree(PRINTING_FULL);
        PrinterStatusUpdate update = adapter.parseBambuPayload(printerId, root);

        assertThat(update.layerCurrent()).isEqualTo(42);
        assertThat(update.layerTotal()).isEqualTo(180);
        assertThat(update.speedPercent()).isEqualTo(100);
    }

    @Test
    void finishedState_mapsToIdle() throws Exception {
        JsonNode root = objectMapper.readTree(IDLE);
        PrinterStatusUpdate update = adapter.parseBambuPayload(printerId, root);

        assertThat(update.state()).isEqualTo(PrinterState.IDLE);
    }

    @Test
    void pausedState_mapsToPaused() throws Exception {
        JsonNode root = objectMapper.readTree(PAUSED);
        PrinterStatusUpdate update = adapter.parseBambuPayload(printerId, root);

        assertThat(update.state()).isEqualTo(PrinterState.PAUSED);
        assertThat(update.currentFile()).isEqualTo("vase.3mf");
    }

    @Test
    void failedState_mapsToError() throws Exception {
        JsonNode root = objectMapper.readTree(FAILED);
        PrinterStatusUpdate update = adapter.parseBambuPayload(printerId, root);

        assertThat(update.state()).isEqualTo(PrinterState.ERROR);
    }

    @Test
    void unknownState_defaultsToIdle() throws Exception {
        JsonNode root = objectMapper.readTree(UNKNOWN_STATE);
        PrinterStatusUpdate update = adapter.parseBambuPayload(printerId, root);

        assertThat(update.state()).isEqualTo(PrinterState.IDLE);
    }

    @Test
    void blankSubtaskName_currentFileIsNull() throws Exception {
        JsonNode root = objectMapper.readTree(IDLE);
        PrinterStatusUpdate update = adapter.parseBambuPayload(printerId, root);

        assertThat(update.currentFile()).isNull();
    }

    // ── parseHms ─────────────────────────────────────────────────────────────

    @Test
    void parseHms_emptyArray_returnsEmptyList() throws Exception {
        JsonNode print = objectMapper.readTree(PRINTING_FULL).path("print");
        List<HmsAlert> alerts = adapter.parseHms(print);

        assertThat(alerts).isEmpty();
    }

    @Test
    void parseHms_twoAlerts_parsedCorrectly() throws Exception {
        JsonNode print = objectMapper.readTree(HMS_ALERTS).path("print");
        List<HmsAlert> alerts = adapter.parseHms(print);

        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).attr()).isEqualTo(50462720L);
        assertThat(alerts.get(0).code()).isEqualTo(65543L);
        assertThat(alerts.get(1).attr()).isEqualTo(33686016L);
        assertThat(alerts.get(1).code()).isEqualTo(12289L);
    }

    // ── parseAms ─────────────────────────────────────────────────────────────

    @Test
    void parseAms_noAmsNode_returnsNull() throws Exception {
        JsonNode print = objectMapper.readTree(NO_AMS).path("print");
        assertThat(adapter.parseAms(print)).isNull();
    }

    @Test
    void parseAms_presentAndPopulated_returnsAmsStatusList() throws Exception {
        JsonNode print = objectMapper.readTree(PRINTING_FULL).path("print");
        List<AmsStatus> result = adapter.parseAms(print);

        assertThat(result).isNotNull().hasSize(1);
        assertThat(result.get(0).unitId()).isEqualTo("0");
        // Slot id=3 has empty tray_type → skipped; expect 3 trays
        assertThat(result.get(0).trays()).hasSize(3);
        // Alpha stripped: "56B7E6FF" → "56B7E6"
        assertThat(result.get(0).trays().get(0).colorHex()).isEqualTo("56B7E6");
    }

    @Test
    void parseAms_trayNowMatchesActiveTray() throws Exception {
        JsonNode print = objectMapper.readTree(PRINTING_FULL).path("print");
        List<AmsStatus> result = adapter.parseAms(print);

        assertThat(result).isNotNull();
        var trays = result.get(0).trays();
        // tray_now = "2" → only tray id=2 (ABS) should be active
        assertThat(trays.stream().filter(t -> t.id().equals("2")).findFirst())
                .hasValueSatisfying(t -> assertThat(t.isActive()).isTrue());
        assertThat(trays.stream().filter(t -> !t.id().equals("2")))
                .allSatisfy(t -> assertThat(t.isActive()).isFalse());
    }

    @Test
    void parseAms_emptyTrayTypeSkipped() throws Exception {
        JsonNode print = objectMapper.readTree(PRINTING_FULL).path("print");
        List<AmsStatus> result = adapter.parseAms(print);

        assertThat(result).isNotNull();
        // Slot id=3 has tray_type="" and must not appear
        assertThat(result.get(0).trays().stream().map(t -> t.id()))
                .doesNotContain("3");
    }
}
