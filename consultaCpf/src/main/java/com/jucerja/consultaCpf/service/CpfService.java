package com.jucerja.consultaCpf.service;

import com.jucerja.consultaCpf.client.CpfSoapClient;
import com.jucerja.consultaCpf.dto.SoapResult;
import com.jucerja.consultaCpf.entity.SituacaoCadastralCPF;
import com.jucerja.consultaCpf.repository.SituacaoCadastralCPFRespository;
import com.jucerja.consultaCpf.soap.response.ConsultaCPFResponse;
import com.jucerja.consultaCpf.soap.response.DadosCPF;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CpfService {

    @Autowired
    private CpfSoapClient soapClient;

    @Autowired
    private SituacaoCadastralCPFRespository situacaoRepository;

    public String verificarSituacao(String cpf) {

        // =========================
        // CHAMA SOAP
        // =========================

        SoapResult soapResult =
                soapClient.consultarCpf(cpf);

        ConsultaCPFResponse response =
                soapResult.response();

        // =========================
        // VALIDA RESPONSE
        // =========================

        if (response == null) {
            return "Resposta SOAP nula";
        }

        if (response.getRetornoWS09Redesim() == null) {
            return "Retorno WS vazio";
        }

        // =========================
        // PEGA LISTA CPF
        // =========================

        List<DadosCPF> listaCpf =
                response
                    .getRetornoWS09Redesim()
                    .getDadosCPF();

        // =========================
        // VALIDA LISTA
        // =========================

        if (listaCpf == null || listaCpf.isEmpty()) {
            return "CPF não encontrado";
        }

        // =========================
        // PEGA PRIMEIRO CPF
        // =========================

        DadosCPF dadosCPF = listaCpf.get(0);

        // =========================
        // PEGA SITUAÇÃO (descrição vem da tabela de domínio)
        // =========================

        String codigoSituacao =
                dadosCPF.getSituacaoCadastral();

        String descricaoSituacao =
                situacaoRepository
                        .findByCodigoSituacaoCPFCadastralRFB(codigoSituacao)
                        .map(SituacaoCadastralCPF::getDescricao)
                        .orElse("Situação desconhecida");

        // =========================
        // DEBUG
        // =========================

        System.out.println("CPF: " + dadosCPF.getNumCPF());
        System.out.println("Nome: " + dadosCPF.getNome());
        System.out.println("Código Situação: " + codigoSituacao);
        System.out.println("Descrição Situação: " + descricaoSituacao);

        return descricaoSituacao;
    }
}