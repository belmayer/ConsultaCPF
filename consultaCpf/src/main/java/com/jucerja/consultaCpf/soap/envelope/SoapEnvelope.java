package com.jucerja.consultaCpf.soap.envelope;

import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter

@XmlRootElement(
        name = "Envelope",
        namespace = "http://www.w3.org/2003/05/soap-envelope"
)
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapEnvelope {

    @XmlElement(
            name = "Body",
            namespace = "http://www.w3.org/2003/05/soap-envelope"
    )
    private SoapBody body;

}