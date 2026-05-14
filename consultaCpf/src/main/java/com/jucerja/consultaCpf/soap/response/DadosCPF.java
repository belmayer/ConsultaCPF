package com.jucerja.consultaCpf.soap.response;

import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class DadosCPF {

    private String numCPF;

    private String nome;

    private String dataNascimento;

    private String situacaoCadastral;

  
}