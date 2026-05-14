package com.jucerja.consultaCpf.soap.envelope;

import com.jucerja.consultaCpf.soap.request.ConsultaCPFRequest;
import com.jucerja.consultaCpf.soap.response.ConsultaCPFResponse;
import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapBody {

    @XmlElement(
            namespace = "http://servicos.integrador.serpro.gov.br/"
    )
    private ConsultaCPFRequest consultaCPFRequest;

    @XmlElement(
            namespace = "http://servicos.integrador.serpro.gov.br/"
    )
    private ConsultaCPFResponse consultaCPFResponse;

}