package com.jucerja.consultaCpf.service;

import com.jucerja.consultaCpf.client.CpfSoapClient;
import com.jucerja.consultaCpf.soap.response.ConsultaCPFResponse;
import com.jucerja.consultaCpf.soap.response.DadosCPF;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CpfService {

    @Autowired
    private CpfSoapClient soapClient;

    public String verificarSituacao(String cpf) {

        // =========================
        // CHAMA SOAP
        // =========================

        ConsultaCPFResponse response =
                soapClient.consultarCpf(cpf);

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
        // PEGA SITUAÇÃO
        // =========================

        String codigoSituacao =
                dadosCPF.getSituacaoCadastral();

        String descricaoSituacao =
                traduzirSituacao(codigoSituacao);

        // =========================
        // DEBUG
        // =========================

        System.out.println("CPF: " + dadosCPF.getNumCPF());
        System.out.println("Nome: " + dadosCPF.getNome());
        System.out.println("Código Situação: " + codigoSituacao);
        System.out.println("Descrição Situação: " + descricaoSituacao);

        return descricaoSituacao;
    }

    // =========================
    // TRADUZ CÓDIGO RFB
    // =========================

    private String traduzirSituacao(String codigo) {

        return switch (codigo) {

            case "0" -> "Regular";

            case "1" -> "Cancelada por encerramento de espólio";

            case "2" -> "Suspensa";

            case "3" -> "Cancelada óbito sem espólio";

            case "4" -> "Pendente de Regularização";

            case "5" -> "Cancelada multiplicidade";

            case "8" -> "Nula";

            case "9" -> "Cancelada de ofício";

            default -> "Situação desconhecida";
        };
    }
}