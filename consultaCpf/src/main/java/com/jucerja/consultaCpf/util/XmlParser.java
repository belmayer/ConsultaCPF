package com.jucerja.consultaCpf.util;

import jakarta.xml.bind.*;

import java.io.StringReader;
import java.io.StringWriter;

public class XmlParser {

    public static String toXml(Object object) {

        try {

            JAXBContext context =
                    JAXBContext.newInstance(object.getClass());

            Marshaller marshaller =
                    context.createMarshaller();

            marshaller.setProperty(
                    Marshaller.JAXB_FORMATTED_OUTPUT,
                    true
            );

            StringWriter writer = new StringWriter();

            marshaller.marshal(object, writer);

            return writer.toString();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Erro ao converter objeto para XML",
                    e
            );
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromXml(String xml, Class<T> clazz) {

        try {

            JAXBContext context =
                    JAXBContext.newInstance(clazz);

            Unmarshaller unmarshaller =
                    context.createUnmarshaller();

            return (T) unmarshaller.unmarshal(
                    new StringReader(xml)
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Erro ao converter XML para objeto",
                    e
            );
        }
    }
}