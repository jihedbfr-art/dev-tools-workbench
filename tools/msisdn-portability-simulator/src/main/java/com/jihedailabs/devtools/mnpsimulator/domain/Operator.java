package com.jihedailabs.devtools.mnpsimulator.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mnp_operator")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Operator {
    
    @Id
    private String code;
    
    private String displayName;
    
    private String routingPrefix;
}
