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

import com.openbravo.pos.forms.AppConfig;
import java.io.File;

/**
 * Settings for the silent update module, stored in unicentaopos.properties.
 */
public final class UpdateSettings {

    public static final String ENABLED = "update.enabled";
    public static final String URL = "update.url";
    public static final String INTERVAL_HOURS = "update.interval.hours";
    public static final String SILENT = "update.silent";
    public static final String DOWNLOAD_DIR = "update.download.dir";
    public static final String AUTO_INSTALL = "update.autoInstall";
    public static final String LAST_CHECK = "update.lastcheck";
    public static final String LAST_VERSION = "update.lastversion";

    public static final String DEFAULT_URL =
            "https://api.github.com/repos/scanby/unicenta/releases/latest";
    public static final String DEFAULT_DIR = ".unicentaopos-updates";

    private UpdateSettings() {
    }

    /** Fills the default values when the properties file does not define them. */
    public static void installDefaults(AppConfig config) {
        if (config.getProperty(ENABLED) == null) {
            config.setProperty(ENABLED, "false");
        }
        if (config.getProperty(URL) == null) {
            config.setProperty(URL, DEFAULT_URL);
        }
        if (config.getProperty(INTERVAL_HOURS) == null) {
            config.setProperty(INTERVAL_HOURS, "24");
        }
        if (config.getProperty(SILENT) == null) {
            config.setProperty(SILENT, "true");
        }
        if (config.getProperty(DOWNLOAD_DIR) == null) {
            config.setProperty(DOWNLOAD_DIR,
                    new File(System.getProperty("user.home"), DEFAULT_DIR).getAbsolutePath());
        }
        if (config.getProperty(AUTO_INSTALL) == null) {
            config.setProperty(AUTO_INSTALL, "false");
        }
    }

    public static boolean isEnabled(AppConfig config) {
        return Boolean.parseBoolean(config.getProperty(ENABLED));
    }

    public static boolean isSilent(AppConfig config) {
        return Boolean.parseBoolean(config.getProperty(SILENT));
    }

    public static boolean isAutoInstall(AppConfig config) {
        return Boolean.parseBoolean(config.getProperty(AUTO_INSTALL));
    }

    public static int getIntervalHours(AppConfig config) {
        try {
            int hours = Integer.parseInt(config.getProperty(INTERVAL_HOURS));
            return hours < 1 ? 24 : hours;
        } catch (NumberFormatException e) {
            return 24;
        }
    }

    public static String getUrl(AppConfig config) {
        String url = config.getProperty(URL);
        if (url == null || !url.startsWith("https://")) {
            return DEFAULT_URL;
        }
        return url;
    }

    public static File getDownloadDir(AppConfig config) {
        String dir = config.getProperty(DOWNLOAD_DIR);
        if (dir == null || dir.trim().length() == 0) {
            dir = new File(System.getProperty("user.home"), DEFAULT_DIR).getAbsolutePath();
        }
        return new File(dir);
    }
}
