package com.jihedailabs.devtools.mnpsimulator.repository;

import com.jihedailabs.devtools.mnpsimulator.domain.Operator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorRepository extends JpaRepository<Operator, String> {
}
