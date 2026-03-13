package com.printerhub.core.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * A physical 3D printer registered in PrinterHub.
 *
 * Key design decisions:
 * - UUID primary key: safe to expose in REST URLs, no sequential guessing.
 * - accessCode stored here for now (Phase 3 moves secrets to a vault / encrypted column).
 * - JPA auditing (@CreatedDate / @LastModifiedDate) fills timestamps automatically —
 *   you need @EnableJpaAuditing on your Spring Boot app class for this to work.
 */
@Entity
@Table(name = "printers")
@EntityListeners(AuditingEntityListener.class)   // enables @CreatedDate / @LastModifiedDate
@Data                   // Lombok: generates getters, setters, equals, hashCode, toString
@NoArgsConstructor      // Lombok: JPA requires a no-arg constructor
public class Printer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false)
    private String name;           // User-assigned label, e.g. "Workshop X1C"

    @Enumerated(EnumType.STRING)   // Store "BAMBU" not "0" — readable in DB
    @Column(nullable = false)
    private PrinterBrand brand;

    private String model;          // e.g. "X1C", "MK4"
    private String serialNumber;   // Used to build Bambu MQTT topics
    private String ipAddress;      // Used for LAN-mode HTTP adapters
    @JsonProperty(access = Access.WRITE_ONLY)   // never serialised in responses; accepted on POST/PATCH
    private String accessCode;     // Bambu LAN access code (move to secrets manager in Phase 3)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrinterState state = PrinterState.OFFLINE;

    private boolean active = true; // Soft-delete flag: false = hidden from dashboard

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
