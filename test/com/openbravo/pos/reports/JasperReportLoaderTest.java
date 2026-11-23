package com.openbravo.pos.reports;

import static org.junit.Assert.*;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.Test;

/**
 * Verifies that versioned .jrxml resources can still be compiled after the
 * loading logic was extracted from JPanelReport.
 */
public class JasperReportLoaderTest {

    @Test
    public void compilesABundledReport() throws Exception {
        JasperReport report = JasperReportLoader.load(
                JasperReportLoaderTest.class, "/com/openbravo/reports/productsales");
        assertNotNull(report);
        assertNotNull(report.getFields());
    }

    @Test
    public void missingReportRaisesIOException() {
        try {
            JasperReportLoader.load(JasperReportLoaderTest.class, "/com/openbravo/reports/does.not.exist");
            fail("Expected IOException");
        } catch (java.io.IOException expected) {
            // ok
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }
}
