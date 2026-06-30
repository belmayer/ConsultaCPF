package com.jucerja.consultaCpf.client;

import com.jucerja.consultaCpf.soap.envelope.SoapBody;
import com.jucerja.consultaCpf.soap.envelope.SoapEnvelope;
import com.jucerja.consultaCpf.soap.request.ConsultaCPFRequest;
import com.jucerja.consultaCpf.soap.response.ConsultaCPFResponse;
import com.jucerja.consultaCpf.util.XmlParser;
import com.jucerja.consultaCpf.dto.SoapResult;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class CpfSoapClient {

    private static final String URL =
            "http://rfb.hml.jucerja.rj.gov.br/services/ws09/ws09";

    public SoapResult consultarCpf(String cpf) {

        try {

            // =========================
            // CRIA REQUEST
            // =========================

            ConsultaCPFRequest consultaRequest =
                    new ConsultaCPFRequest();

            consultaRequest.setCodServico("S09");
            consultaRequest.setVersao("100000");
            consultaRequest.setNumeroProtocolo("RJP1234567890");
            consultaRequest.setNumeroOcorrencia(1);
            consultaRequest.setCodEvento("101");
            consultaRequest.setCpf(cpf);

            // =========================
            // CRIA BODY SOAP
            // =========================

            SoapBody body = new SoapBody();
            body.setConsultaCPFRequest(consultaRequest);

            // =========================
            // CRIA ENVELOPE SOAP
            // =========================

            SoapEnvelope envelope = new SoapEnvelope();
            envelope.setBody(body);

            // =========================
            // CONVERTE OBJETO -> XML
            // =========================

            String xml = XmlParser.toXml(envelope);

            // =========================
            // HTTP CLIENT
            // =========================

            HttpClient httpClient = HttpClients.createDefault();

            HttpComponentsClientHttpRequestFactory factory =
                    new HttpComponentsClientHttpRequestFactory(httpClient);

            RestTemplate restTemplate =
                    new RestTemplate(factory);

            // =========================
            // HEADERS
            // =========================

            HttpHeaders headers = new HttpHeaders();

            headers.set(
                    "Content-Type",
                    "application/soap+xml;charset=UTF-8;" +
                    "action=\"/ws09-service0.serviceagent/WS09Endpoint0/consultaCPF\""
            );

            headers.set("Accept-Encoding", "gzip,deflate");

            // =========================
            // REQUEST
            // =========================

            HttpEntity<String> request =
                    new HttpEntity<>(xml, headers);

            // =========================
            // CHAMADA SOAP
            // =========================

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            URL,
                            HttpMethod.POST,
                            request,
                            String.class
                    );

            // =========================
            // XML -> OBJETO
            // =========================

            SoapEnvelope responseEnvelope =
                    XmlParser.fromXml(
                            response.getBody(),
                            SoapEnvelope.class
                    );

            return responseEnvelope
                    .getBody()
                    .getConsultaCPFResponse();

        } catch (HttpStatusCodeException e) {

            throw new RuntimeException(
                    "ERRO SOAP: " + e.getResponseBodyAsString()
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "ERRO AO CONSULTAR CPF: " + e.getMessage()
            );
        }
    }
}