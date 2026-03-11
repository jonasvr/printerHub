package com.printerhub.core.repository;

import com.printerhub.core.entity.PrintJob;
import com.printerhub.core.entity.PrinterState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {

    // All jobs for a given printer, newest first
    List<PrintJob> findAllByPrinterIdOrderByCreatedAtDesc(UUID printerId);

    // The currently active job (should be at most one per printer)
    Optional<PrintJob> findByPrinterIdAndState(UUID printerId, PrinterState state);
}
