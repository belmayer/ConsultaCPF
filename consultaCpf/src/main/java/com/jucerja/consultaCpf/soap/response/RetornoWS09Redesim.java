package com.jucerja.consultaCpf.soap.response;

import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class RetornoWS09Redesim {

    private String codServico;

    private String versao;

    @XmlElement(name = "dadosCPF")
    private List<DadosCPF> dadosCPF;

}