package com.roshka.sifen.internal.request;

import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.internal.SOAPResponse;
import com.roshka.sifen.internal.helpers.SoapHelper;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses recorded SIFEN responses the way the library does after a request: the bytes go through
 * SoapHelper.parseSoapMessage (as in SoapHelper.makeSoapRequest) and then through the
 * processResponse of the matching request. The resulting response object is written out as
 * text, every getter recursively and in sorted order, including getRespuestaBruta(). The text
 * must be identical between the baseline and the version under test.
 * The request type comes from the file name: recepcion-de-*, recepcion-lote-*, consulta-lote-*,
 * recepcion-evento-*, consulta-ruc-*, consulta-de-*.
 * Arguments: outDir response.xml [response.xml ...]
 */
public class ResponseDump {
    public static void main(String[] a) throws Exception {
        Path out = Paths.get(a[0]);
        Files.createDirectories(out);
        SifenConfig cfg = new SifenConfig();

        for (int i = 1; i < a.length; i++) {
            Path file = Paths.get(a[i]);
            String name = file.getFileName().toString().replaceAll("\\.xml$", "");
            StringBuilder text = new StringBuilder();
            try {
                byte[] raw = Files.readAllBytes(file);
                // As in SoapHelper.makeSoapRequest
                SOAPResponse response = new SOAPResponse();
                int status = name.contains("fault") || name.contains("no-soap") ? 500 : 200;
                response.setStatus(status);
                response.setSoapResponse(SoapHelper.parseSoapMessage(new ByteArrayInputStream(raw)));
                response.setRawData(raw);
                Object parsed = request(name, cfg).processResponse(response);
                dump(text, "", parsed, new IdentityHashMap<>());
            } catch (Throwable t) {
                text.append("EXCEPTION ").append(t.getClass().getName());
                // Java 14+ adds its own "helpful" text to NullPointerException; it is not library output.
                if (!(t instanceof NullPointerException)) text.append(": ").append(t.getMessage());
                text.append('\n');
            }
            Files.write(out.resolve(name + ".txt"), text.toString().getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("java " + System.getProperty("java.version") + ": " + (a.length - 1) + " responses done");
    }

    private static BaseRequest request(String name, SifenConfig cfg) {
        if (name.startsWith("recepcion-de-")) return new ReqRecDe(1, cfg);
        if (name.startsWith("recepcion-lote-")) return new ReqRecLoteDe(1, cfg);
        if (name.startsWith("consulta-lote-")) return new ReqConsLoteDe(1, cfg);
        if (name.startsWith("recepcion-evento-")) return new ReqRecEventoDe(1, cfg);
        if (name.startsWith("consulta-ruc-")) return new ReqConsRuc(1, cfg);
        if (name.startsWith("consulta-de-")) return new ReqConsDe(1, cfg);
        throw new IllegalArgumentException("Unknown response type: " + name);
    }

    private static void dump(StringBuilder s, String path, Object value, Map<Object, Boolean> seen) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum || value.getClass().getName().startsWith("java.time.")) {
            s.append(path).append(" = ").append(format(value)).append('\n');
            return;
        }
        if (value instanceof byte[]) {
            s.append(path).append(" = bytes[").append(((byte[]) value).length).append("]\n");
            return;
        }
        if (value instanceof Collection) {
            int i = 0;
            s.append(path).append(" = list[").append(((Collection<?>) value).size()).append("]\n");
            for (Object item : (Collection<?>) value) dump(s, path + "[" + i++ + "]", item, seen);
            return;
        }
        if (!value.getClass().getName().startsWith("com.roshka.")) {
            s.append(path).append(" = <").append(value.getClass().getName()).append(">\n");
            return;
        }
        if (seen.put(value, Boolean.TRUE) != null) {
            s.append(path).append(" = <cycle>\n");
            return;
        }
        List<Method> getters = new ArrayList<>();
        for (Method m : value.getClass().getMethods()) {
            boolean getter = (m.getName().startsWith("get") || m.getName().startsWith("is")) && m.getParameterCount() == 0;
            if (getter && !Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass() != Object.class) getters.add(m);
        }
        getters.sort(Comparator.comparing(Method::getName));
        for (Method m : getters) {
            Object child;
            try {
                child = m.invoke(value);
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                s.append(path).append('.').append(m.getName()).append(" = EXCEPTION ").append(cause.getClass().getName()).append('\n');
                continue;
            }
            dump(s, path + "." + m.getName(), child, seen);
        }
        seen.remove(value);
    }

    private static String format(Object value) {
        if (value == null) return "null";
        if (value instanceof Enum) return value.getClass().getSimpleName() + "." + ((Enum<?>) value).name();
        if (value instanceof String) {
            return "\"" + ((String) value).replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
        }
        return value.toString();
    }
}
