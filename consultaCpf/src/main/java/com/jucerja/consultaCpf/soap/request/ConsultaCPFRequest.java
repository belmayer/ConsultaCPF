package com.jucerja.consultaCpf.soap.request;

import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@XmlRootElement(
        name = "consultaCPFRequest",
        namespace = "http://servicos.integrador.serpro.gov.br/"
)
@XmlAccessorType(XmlAccessType.FIELD)
public class ConsultaCPFRequest {

    private String codServico;

    private String versao;

    private String numeroProtocolo;

    private Integer numeroOcorrencia;

    private String codEvento;

    private String cpf;

}