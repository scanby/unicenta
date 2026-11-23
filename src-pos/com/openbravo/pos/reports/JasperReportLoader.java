//    uniCenta oPOS  - Touch Friendly Point Of Sale
//    Copyright (c) 2009-2014 uniCenta
//    http://www.unicenta.com
//
//    This file is part of uniCenta oPOS
//
//    uniCenta oPOS is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//   uniCenta oPOS is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with uniCenta oPOS.  If not, see <http://www.gnu.org/licenses/>.

package com.openbravo.pos.reports;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

/**
 * Loads a Jasper report from a precompiled {@code .ser} resource when present,
 * otherwise compiles the versioned {@code .jrxml} source at runtime.
 *
 * <p>Kept separate from {@link JPanelReport} so the expensive compilation can
 * be moved to a background thread without touching the Swing component.</p>
 */
public final class JasperReportLoader {

    private JasperReportLoader() {
    }

    /**
     * @param context    class whose classloader is used to find the resource
     * @param reportPath classpath path without extension, e.g.
     *                   {@code /com/openbravo/reports/productsales}
     * @return the compiled report
     * @throws JRException           if the report definition is invalid
     * @throws IOException           if neither .ser nor .jrxml can be read
     * @throws ClassNotFoundException if a serialized report cannot be read
     */
    public static JasperReport load(Class<?> context, String reportPath)
            throws JRException, IOException, ClassNotFoundException {
        InputStream in = context.getResourceAsStream(reportPath + ".ser");
        if (in != null) {
            try (ObjectInputStream oin = new ObjectInputStream(in)) {
                return (JasperReport) oin.readObject();
            }
        }

        InputStream xml = context.getResourceAsStream(reportPath + ".jrxml");
        if (xml == null) {
            throw new IOException("Report resource not found: " + reportPath + ".jrxml");
        }
        try {
            JasperDesign jd = JRXmlLoader.load(xml);
            return JasperCompileManager.compileReport(jd);
        } finally {
            xml.close();
        }
    }
}
