import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.*;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;

// Self-signed test certificate shaped like a Paraguayan one: SERIALNUMBER in the subject and
// directoryName entries in the subject alternative names (plus an e-mail entry to be skipped).
public class GenCert {
    public static void main(String[] a) throws Exception {
        CertAndKeyGen g = new CertAndKeyGen("RSA", "SHA256withRSA");
        g.generate(2048);
        GeneralNames names = new GeneralNames();
        names.add(new GeneralName(new RFC822Name("prueba@example.com")));
        names.add(new GeneralName(new X500Name("CN=JUAN PEREZ, SERIALNUMBER=CI1234567, O=PERSONA FISICA")));
        names.add(new GeneralName(new X500Name("SERIALNUMBER=RUC80000001-2, CN=EMPRESA PRUEBA SA, O=EMPRESA PRUEBA SA, C=PY")));
        CertificateExtensions ext = new CertificateExtensions();
        ext.set(SubjectAlternativeNameExtension.NAME, new SubjectAlternativeNameExtension(names));
        X509Certificate cert = g.getSelfCertificate(
                new X500Name("CN=EMPRESA PRUEBA SA, SERIALNUMBER=RUC80000001-2, O=EMPRESA PRUEBA SA, C=PY"),
                new Date(), 3650L * 24 * 3600, ext);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("test", g.getPrivateKey(), "test".toCharArray(), new X509Certificate[]{cert});
        try (FileOutputStream o = new FileOutputStream(a[0])) { ks.store(o, "test".toCharArray()); }
    }
}
