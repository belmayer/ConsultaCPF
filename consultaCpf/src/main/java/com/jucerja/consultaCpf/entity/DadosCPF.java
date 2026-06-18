package com.jucerja.consultaCpf.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
    @Column(name = "Id", nullable = false)
    private Integer id;
 
    /*
     * CPF armazenado como varchar(14) — pode conter máscara (123.456.789-00).
     * Antes de enviar ao serviço SOAP, remover caracteres não numéricos:
     *   cpf.replaceAll("[^0-9]", "")
     */
    @Column(name = "CPF", nullable = false, length = 14)
    private String cpf;
 
    @Column(name = "Nome", length = 150)
    private String nome;
 
    @Column(name = "DataInclusao", nullable = false)
    private LocalDateTime dataInclusao;
 
    /*
     * Preenchido após a consulta ao serviço S09.
     * Nulo indica que o CPF ainda não foi processado (pendente).
     */
    @Column(name = "DataAtualizacao")
    private LocalDateTime dataAtualizacao;
 
    /*
     * FK para SituacaoCadastralCPF.
     * Nulo enquanto o CPF não foi consultado.
     * FetchType.LAZY: a situação só é carregada quando acessada explicitamente.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SituacaoCadastralRFBId")
    private SituacaoCadastralCPF situacaoCadastral;
 
    /*
     * FK para ConsultaCPFS09.
     * Nulo enquanto o CPF não foi consultado.
     * Preenchido após salvar o XML na tabela ConsultaCPFS09.
     *
     * ATENÇÃO — dependência circular:
     * ConsultaCPFS09 também tem FK para DadosCPF (DadosCPFId).
     * Ordem correta de operação:
     *   1. Inserir em ConsultaCPFS09 (usando o Id desta entidade já existente)
     *   2. Atualizar este campo com o Id gerado para ConsultaCPFS09
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ConsultaS09Id")
    private ConsultaCPFS09 consultaS09;
    
}
