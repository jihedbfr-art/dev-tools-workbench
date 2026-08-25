package com.jihedailabs.devtools.mnpsimulator.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "mnp_ported_number")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PortedNumber {
    
    @Id
    private String msisdn;
    
    @ManyToOne
    @JoinColumn(name = "donor_operator_code")
    private Operator donorOperator;
    
    @ManyToOne
    @JoinColumn(name = "current_operator_code")
    private Operator currentOperator;
    
    private Instant lastPortedAt;
    
    public boolean isPorted() {
        if (donorOperator == null || currentOperator == null) {
            return false;
        }
        return !donorOperator.getCode().equals(currentOperator.getCode());
    }
}
