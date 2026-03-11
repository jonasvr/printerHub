package com.printerhub.core.repository;

import com.printerhub.core.entity.Printer;
import com.printerhub.core.entity.PrinterBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Printer.
 *
 * Extending JpaRepository<Printer, UUID> gives us save(), findById(),
 * findAll(), delete(), etc. for free — no SQL needed.
 * Custom queries are derived from the method name by Spring at startup.
 */
@Repository
public interface PrinterRepository extends JpaRepository<Printer, UUID> {

    // SELECT * FROM printers WHERE active = true
    List<Printer> findAllByActiveTrue();

    // SELECT * FROM printers WHERE brand = ? AND active = true
    List<Printer> findAllByBrandAndActiveTrue(PrinterBrand brand);

    // SELECT * FROM printers WHERE serial_number = ?
    Optional<Printer> findBySerialNumber(String serialNumber);
}
