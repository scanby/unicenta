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

package com.openbravo.pos.updates;

import com.openbravo.data.user.DirtyManager;
import com.openbravo.pos.config.PanelConfig;
import com.openbravo.pos.forms.AppConfig;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 * "Actualizaciones" tab of the general configuration dialog.
 * The check/download itself is completely silent; this panel only shows the
 * result in a label, exactly like the startup background check does in the log.
 */
public class JPanelConfigUpdates extends JPanel implements PanelConfig {

    private final DirtyManager dirty = new DirtyManager();
    private final JCheckBox jchkEnabled = new JCheckBox("Buscar actualizaciones automaticamente (silencioso)");
    private final JCheckBox jchkSilent = new JCheckBox("Modo silencioso (sin ventanas ni mensajes)");
    private final JCheckBox jchkAutoInstall = new JCheckBox("Instalar automaticamente tras descargar (avanzado)");
    private final JTextField jtxtUrl = new JTextField(28);
    private final JTextField jtxtInterval = new JTextField(6);
    private final JTextField jtxtDir = new JTextField(28);
    private final JLabel jlblStatus = new JLabel(" ");
    private final JButton jbtnCheck = new JButton("Buscar e instalar ahora");
    private final JButton jbtnInstall = new JButton("Instalar descarga pendiente");

    private AppConfig config;

    public JPanelConfigUpdates() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        c.gridwidth = 2;
        add(jchkEnabled, c);
        jchkEnabled.addActionListener(dirty);

        c.gridy = 1;
        add(jchkSilent, c);
        jchkSilent.addActionListener(dirty);

        c.gridy = 2;
        add(jchkAutoInstall, c);
        jchkAutoInstall.addActionListener(dirty);

        c.gridy = 3;
        c.gridwidth = 1;
        add(new JLabel("URL del servidor:"), c);
        c.gridx = 1;
        add(jtxtUrl, c);
        jtxtUrl.getDocument().addDocumentListener(dirty);

        c.gridy = 4;
        c.gridx = 0;
        add(new JLabel("Intervalo (horas):"), c);
        c.gridx = 1;
        add(jtxtInterval, c);
        jtxtInterval.getDocument().addDocumentListener(dirty);

        c.gridy = 5;
        c.gridx = 0;
        add(new JLabel("Directorio de descarga:"), c);
        c.gridx = 1;
        add(jtxtDir, c);
        jtxtDir.getDocument().addDocumentListener(dirty);

        c.gridy = 6;
        c.gridx = 0;
        c.gridwidth = 2;
        add(jbtnCheck, c);

        c.gridy = 7;
        add(jbtnInstall, c);

        c.gridy = 8;
        add(jlblStatus, c);

        jbtnCheck.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkNow();
            }
        });
        jbtnInstall.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                installPending();
            }
        });
    }

    private void installPending() {
        File pending = UpdateService.findLatestDownload(config);
        if (pending == null) {
            jlblStatus.setText("No hay ninguna descarga pendiente.");
            return;
        }
        boolean ok = UpdateService.scheduleInstallAfterExit(config, pending);
        jlblStatus.setText(ok
                ? "Instalacion programada al cerrar el programa. Vuelva a abrir la aplicacion."
                : "No se pudo programar la instalacion de " + pending.getName() + ". Consulte el log.");
    }

    private void checkNow() {
        if (config == null) {
            return;
        }
        jbtnCheck.setEnabled(false);
        jlblStatus.setText("Comprobando actualizaciones...");
        final AppConfig cfg = config;
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                UpdateInfo info = UpdateService.check(cfg);
                if (info == null) {
                    return "No hay actualizaciones disponibles.";
                }
                File downloaded = UpdateService.download(cfg, info);
                if (downloaded == null) {
                    return "Hay una actualizacion (" + info.getVersion()
                            + ") pero no se pudo descargar. Consulte el log.";
                }
                cfg.setProperty(UpdateSettings.LAST_VERSION, info.getVersion());
                // A manual click always tries to install; the autoInstall flag
                // only controls the unattended background check.
                boolean scheduled = UpdateService.scheduleInstallAfterExit(cfg, downloaded);
                return scheduled
                        ? "Actualizacion " + info.getVersion() + " descargada e instalada al cerrar. "
                          + "Vuelva a abrir la aplicacion."
                        : "Descargada " + info.getVersion() + " pero no se pudo programar. "
                          + "Use 'Instalar descarga pendiente' o consulte el log.";
            }

            @Override
            protected void done() {
                try {
                    jlblStatus.setText(get());
                } catch (Exception e) {
                    jlblStatus.setText("Error al comprobar actualizaciones. Consulte el log.");
                } finally {
                    jbtnCheck.setEnabled(true);
                }
            }
        }.execute();
    }

    @Override
    public boolean hasChanged() {
        return dirty.isDirty();
    }

    @Override
    public Component getConfigComponent() {
        return this;
    }

    @Override
    public void loadProperties(AppConfig config) {
        this.config = config;
        UpdateSettings.installDefaults(config);
        jchkEnabled.setSelected(Boolean.parseBoolean(config.getProperty(UpdateSettings.ENABLED)));
        jchkSilent.setSelected(Boolean.parseBoolean(config.getProperty(UpdateSettings.SILENT)));
        jchkAutoInstall.setSelected(Boolean.parseBoolean(config.getProperty(UpdateSettings.AUTO_INSTALL)));
        jtxtUrl.setText(config.getProperty(UpdateSettings.URL));
        jtxtInterval.setText(config.getProperty(UpdateSettings.INTERVAL_HOURS));
        jtxtDir.setText(config.getProperty(UpdateSettings.DOWNLOAD_DIR));
        jlblStatus.setText(" ");
        dirty.setDirty(false);
    }

    @Override
    public void saveProperties(AppConfig config) {
        this.config = config;
        config.setProperty(UpdateSettings.ENABLED, Boolean.toString(jchkEnabled.isSelected()));
        config.setProperty(UpdateSettings.SILENT, Boolean.toString(jchkSilent.isSelected()));
        config.setProperty(UpdateSettings.AUTO_INSTALL, Boolean.toString(jchkAutoInstall.isSelected()));
        String url = jtxtUrl.getText().trim();
        config.setProperty(UpdateSettings.URL,
                url.startsWith("https://") ? url : UpdateSettings.DEFAULT_URL);
        try {
            int hours = Integer.parseInt(jtxtInterval.getText().trim());
            config.setProperty(UpdateSettings.INTERVAL_HOURS, Integer.toString(hours < 1 ? 24 : hours));
        } catch (NumberFormatException e) {
            config.setProperty(UpdateSettings.INTERVAL_HOURS, "24");
        }
        String dir = jtxtDir.getText().trim();
        config.setProperty(UpdateSettings.DOWNLOAD_DIR,
                dir.length() == 0
                        ? new File(System.getProperty("user.home"), UpdateSettings.DEFAULT_DIR).getAbsolutePath()
                        : dir);
        dirty.setDirty(false);
    }
}
