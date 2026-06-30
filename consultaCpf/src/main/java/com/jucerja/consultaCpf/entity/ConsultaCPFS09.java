package com.jucerja.consultaCpf.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;      // NOVO
import jakarta.persistence.GenerationType;      // NOVO
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ConsultaCPFS09")
public class ConsultaCPFS09 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // NOVO — IDENTITY gera o Id no INSERT
    @Column(name = "Id", nullable = false)
    private Integer id;

    /*
     * FK real para DadosCPF (FK_ConsultaCPFS09 → DadosCPF.Id).
     * Obrigatória: toda consulta pertence a um CPF já existente.
     * Cardinalidade: 1 DadosCPF : N ConsultaCPFS09 (histórico de consultas).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DadosCPFId", nullable = false)
    private DadosCPF dadosCpf;

    /*
     * Coluna xml nativa do SQL Server (driver retorna String).
     * Agora NULLABLE no banco — permite registrar a consulta mesmo sem XML.
     */
    @Column(name = "XMLConsultaCPFS09", nullable = true, columnDefinition = "xml")  // era nullable=false
    private String xmlConsultaCpfS09;
}