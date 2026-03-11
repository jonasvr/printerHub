package com.printerhub.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single print job on a printer.
 *
 * The pauseTimestamps list is our unique differentiator from competing tools:
 * users (or automation) can log pause events with a reason, giving a complete
 * history of why a print was interrupted and for how long.
 */
@Entity
@Table(name = "print_jobs")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class PrintJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Many jobs belong to one printer; FK column is printer_id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id", nullable = false)
    private Printer printer;

    private String filename;         // e.g. "benchy.3mf"
    private int estimatedMinutes;    // from slicer metadata
    private double progressPercent;  // 0–100, updated by adapter

    @Enumerated(EnumType.STRING)
    private PrinterState state = PrinterState.IDLE;

    private Instant startedAt;
    private Instant finishedAt;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    /**
     * Manual pause log — each entry is a JSON-serialised PauseEvent.
     * Stored as a simple string list for Phase 1; Phase 3 promotes this
     * to a proper @OneToMany child table with user attribution.
     *
     * ElementCollection stores each item in a separate join table (print_job_pause_timestamps).
     */
    @ElementCollection
    @CollectionTable(name = "print_job_pause_timestamps",
                     joinColumns = @JoinColumn(name = "print_job_id"))
    @Column(name = "pause_timestamp")
    private List<Instant> pauseTimestamps = new ArrayList<>();
}
