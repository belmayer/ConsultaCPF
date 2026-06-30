package com.jucerja.consultaCpf.dto;

import com.jucerja.consultaCpf.soap.response.ConsultaCPFResponse;

public record SoapResult(
        String xmlBruto,
        ConsultaCPFResponse response
) {
}