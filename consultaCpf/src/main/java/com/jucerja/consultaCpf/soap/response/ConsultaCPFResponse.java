package com.jucerja.consultaCpf.soap.response;

import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@XmlRootElement(
        name = "consultaCPFResponse",
        namespace = "http://servicos.integrador.serpro.gov.br/"
)
@XmlAccessorType(XmlAccessType.FIELD)
public class ConsultaCPFResponse {

    private RetornoWS09Redesim retornoWS09Redesim;

   
}