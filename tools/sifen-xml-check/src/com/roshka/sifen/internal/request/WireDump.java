package com.roshka.sifen.internal.request;

import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.beans.EventosDE;
import com.roshka.sifen.core.beans.ValidezFirmaDigital;
import com.roshka.sifen.core.fields.request.event.TgGroupTiEvt;
import com.roshka.sifen.core.fields.request.event.TrGeVeCan;
import com.roshka.sifen.core.fields.request.event.TrGeVeInu;
import com.roshka.sifen.core.fields.request.event.TrGesEve;
import com.roshka.sifen.core.types.TTiDE;
import com.roshka.sifen.internal.ctx.GenerationCtx;
import org.w3c.dom.Node;

import javax.xml.soap.SOAPMessage;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Builds the SIFEN requests (DE, lot, events) through the library's own setupSoapMessage and
 * serializes them exactly as BaseRequest.makeRequest and SoapHelper.makeSoapRequest do. Each
 * output file must be byte-identical between the baseline and the version under test:
 * <ul>
 *   <li>{@code <case>.wire.xml}: bytes of soapMessage.writeTo(...), what is sent to SIFEN</li>
 *   <li>{@code <case>.sent.xml}: the Transformer string, returned as getRequestSent()</li>
 *   <li>{@code <case>.generarXml.xml}: DocumentoElectronico.generarXml(...)</li>
 *   <li>{@code lote.zip-*}: the zipped rLoteDE (zip timestamp zeroed, see dump)</li>
 *   <li>{@code validation.txt}: Sifen.validarFirmaDEDesdeXml on the generated documents</li>
 * </ul>
 * It lives in the library's package to reach the package-private setupSoapMessage.
 * Arguments: pfx outDir sample.xml [sample.xml ...]
 */
public class WireDump {
    public static void main(String[] a) throws Exception {
        Path out = Paths.get(a[1]);
        Files.createDirectories(out);
        SifenConfig cfg = new SifenConfig(SifenConfig.TipoAmbiente.DEV, "1", "ABCD0000000000000000000000000000",
                SifenConfig.TipoCertificadoCliente.PFX, a[0], "test");
        cfg.setHabilitarNotaTecnica13(true);
        GenerationCtx ctx = GenerationCtx.getDefaultFromConfig(cfg);

        List<String> samples = new ArrayList<>();
        for (int i = 2; i < a.length; i++) {
            samples.add(new String(Files.readAllBytes(Paths.get(a[i])), StandardCharsets.UTF_8));
        }

        StringBuilder validation = new StringBuilder();
        for (int i = 0; i < samples.size(); i++) {
            String name = "de" + (i + 1);
            ReqRecDe request = new ReqRecDe(1000 + i, cfg);
            request.setDE(new DocumentoElectronico(samples.get(i)));
            dump(out, name, request.setupSoapMessage(ctx), false);

            String xml = new DocumentoElectronico(samples.get(i)).generarXml(ctx);
            Files.write(out.resolve(name + ".generarXml.xml"), xml.getBytes(StandardCharsets.UTF_8));
            validation.append(name).append(": ").append(str(Sifen.validarFirmaDEDesdeXml(xml))).append('\n');
            validation.append(name).append(" tampered: ")
                    .append(str(Sifen.validarFirmaDEDesdeXml(xml.replace("<dNumDoc>", "<dNumDoc>9")))).append('\n');
        }

        ReqRecLoteDe lote = new ReqRecLoteDe(2000, cfg);
        List<DocumentoElectronico> deList = new ArrayList<>();
        for (String sample : samples) deList.add(new DocumentoElectronico(sample));
        lote.setDEList(deList);
        dump(out, "lote", lote.setupSoapMessage(ctx), true);

        // Cancelación of the first document
        TrGeVeCan cancelacion = new TrGeVeCan();
        cancelacion.setId(deList.get(0).getId());
        cancelacion.setmOtEve("Prueba de cancelación ñ áéíóú");
        TgGroupTiEvt tiEvtCan = new TgGroupTiEvt();
        tiEvtCan.setrGeVeCan(cancelacion);
        dump(out, "evento-cancelacion", eventRequest(cfg, ctx, 3000, "1", tiEvtCan), false);

        // Inutilización of a number range
        TrGeVeInu inutilizacion = new TrGeVeInu();
        inutilizacion.setdNumTim(12345678);
        inutilizacion.setdEst("001");
        inutilizacion.setdPunExp("002");
        inutilizacion.setdNumIn("0000150");
        inutilizacion.setdNumFin("0000155");
        inutilizacion.setiTiDE(TTiDE.FACTURA_ELECTRONICA);
        inutilizacion.setmOtEve("Inutilización por error de impresión");
        TgGroupTiEvt tiEvtInu = new TgGroupTiEvt();
        tiEvtInu.setrGeVeInu(inutilizacion);
        dump(out, "evento-inutilizacion", eventRequest(cfg, ctx, 3001, "2", tiEvtInu), false);

        Files.write(out.resolve("validation.txt"), validation.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("java " + System.getProperty("java.version") + ": " + samples.size() + " samples done");
    }

    private static SOAPMessage eventRequest(SifenConfig cfg, GenerationCtx ctx, long dId, String id, TgGroupTiEvt tiEvt) throws Exception {
        TrGesEve evento = new TrGesEve();
        evento.setId(id);
        evento.setdFecFirma(LocalDateTime.of(2026, 3, 20, 10, 30, 0));
        evento.setgGroupTiEvt(tiEvt);
        EventosDE eventos = new EventosDE();
        eventos.setrGesEveList(Collections.singletonList(evento));
        ReqRecEventoDe request = new ReqRecEventoDe(dId, cfg);
        request.setEventoDE(eventos);
        return request.setupSoapMessage(ctx);
    }

    private static void dump(Path out, String name, SOAPMessage message, boolean lot) throws Exception {
        if (lot) {
            // The lot zip carries the current DOS date and time; zero them so that runs compare byte by byte.
            Node xDE = message.getSOAPBody().getElementsByTagNameNS("*", "xDE").item(0);
            byte[] zip = Base64.getDecoder().decode(xDE.getTextContent());
            try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
                ZipEntry entry = z.getNextEntry();
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = z.read(buffer)) > 0) content.write(buffer, 0, n);
                // The entry name carries the current date (DE_ddMMyyyy.xml); keep only its shape.
                Files.write(out.resolve(name + ".zip-entry-name.txt"),
                        entry.getName().replaceAll("[0-9]", "N").getBytes(StandardCharsets.UTF_8));
                Files.write(out.resolve(name + ".zip-content.xml"), content.toByteArray());
            }
            for (int i = 0; i + 4 <= zip.length; i++) {
                int signature = (zip[i] & 0xff) | (zip[i + 1] & 0xff) << 8 | (zip[i + 2] & 0xff) << 16 | (zip[i + 3] & 0xff) << 24;
                if (signature == 0x04034b50) Arrays.fill(zip, i + 10, i + 14, (byte) 0); // local file header
                if (signature == 0x02014b50) Arrays.fill(zip, i + 12, i + 16, (byte) 0); // central directory
            }
            xDE.setTextContent(new String(Base64.getEncoder().encode(zip), StandardCharsets.UTF_8));
        }

        // As in BaseRequest.makeRequest
        message.setProperty(SOAPMessage.WRITE_XML_DECLARATION, "true");
        message.setProperty(SOAPMessage.CHARACTER_SET_ENCODING, "UTF-8");
        StringWriter sent = new StringWriter();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(message.getSOAPPart()), new StreamResult(sent));
        Files.write(out.resolve(name + ".sent.xml"), sent.toString().getBytes(StandardCharsets.UTF_8));

        // As in SoapHelper.makeSoapRequest
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        message.writeTo(wire);
        Files.write(out.resolve(name + ".wire.xml"), wire.toByteArray());
    }

    private static String str(ValidezFirmaDigital v) {
        StringBuilder s = new StringBuilder("valido=" + v.isValido() + " motivo=" + v.getMotivoInvalidez());
        if (v.getSujetosCertificado() != null) {
            for (ValidezFirmaDigital.SujetoCertificado subject : v.getSujetosCertificado()) {
                s.append(" | ").append(subject.getNumeroDocumento()).append(" / ").append(subject.getRazonSocial());
            }
        }
        return s.toString();
    }
}
