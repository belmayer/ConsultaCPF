package com.jucerja.consultaCpf.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
@Table(name = "SituacaoCadastralCPF")
public class SituacaoCadastralCPF {
    
    @Id
    @Column(name = "Id", nullable = false)
    private Integer id;
 
    @Column(name = "CodigoSituacaoCPFCadastralRFB", nullable = false, length = 2)
    private String codigoSituacaoCPFCadastralRFB;
 
    @Column(name = "Descricao", nullable = false, length = 50)
    private String descricao;
    
}
