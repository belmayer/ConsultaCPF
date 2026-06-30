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

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "DadosCPF")
public class DadosCPF {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // NOVO
    @Column(name = "Id", nullable = false)
    private Integer id;

    /*
     * CPF varchar(14) — pode conter máscara (123.456.789-00).
     * Antes de enviar ao SOAP, normalizar: cpf.replaceAll("[^0-9]", "")
     */
    @Column(name = "CPF", nullable = false, length = 14)
    private String cpf;

    @Column(name = "Nome", length = 150)
    private String nome;

    @Column(name = "DataInclusao", nullable = false)
    private LocalDateTime dataInclusao;

    /*
     * Preenchido após a consulta ao S09.
     * Nulo = CPF ainda não processado (pendente).
     */
    @Column(name = "DataAtualizacao")
    private LocalDateTime dataAtualizacao;

    /*
     * FK real para SituacaoCadastralCPF.
     * Nulo enquanto o CPF não foi consultado.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SituacaoCadastralRFBId")
    private SituacaoCadastralCPF situacaoCadastral;

    /*
     * Ponteiro para a CONSULTA CORRENTE em ConsultaCPFS09.
     * ATENÇÃO: esta coluna (ConsultaS09Id) NÃO possui mais constraint de FK no banco.
     * O relacionamento real é 1 DadosCPF : N ConsultaCPFS09 (histórico);
     * este campo guarda apenas a última consulta que gerou a situação atual.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ConsultaS09Id")
    private ConsultaCPFS09 consultaS09;

}