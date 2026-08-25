package com.jihedailabs.devtools.mnpsimulator.repository;

import com.jihedailabs.devtools.mnpsimulator.domain.PortedNumber;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortedNumberRepository extends JpaRepository<PortedNumber, String> {
}
