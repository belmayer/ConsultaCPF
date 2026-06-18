package com.jucerja.consultaCpf.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import lombok.Getter;
import lombok.Setter;
 
@Getter
@Setter
@Entity
@Table(name = "ConsultaCPFS09")
public class ConsultaCPFS09 {
    
    @Id
    @Column(name = "Id", nullable = false)
    private Integer id;
 
    /*
     * Relacionamento com DadosCPF.
     * FetchType.LAZY: o objeto DadosCpfEntity só é carregado do banco quando
     * acessado explicitamente, evitando queries desnecessárias.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DadosCPFId", nullable = false)
    private DadosCPF dadosCpf;
 
    /*
     * Coluna do tipo xml nativo do SQL Server.
     * O driver JDBC da Microsoft retorna o conteúdo como String,
     * portanto o mapeamento para String funciona na prática.
     * columnDefinition = "xml" garante que o Hibernate não tente
     * inferir o tipo e evita erros de validação de schema (ddl-auto=validate).
     */
    @Column(name = "XMLConsultaCPFS09", nullable = false, columnDefinition = "xml")
    private String xmlConsultaCpfS09;
}
