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
import com.openbravo.pos.forms.AppLocal;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Silent update service.
 *
 * <p>Checks a HTTPS JSON endpoint (the GitHub "latest release" API by default),
 * compares versions, downloads the Windows installer to a local directory and
 * optionally starts it with no UI at all. Every step is logged; no dialog is
 * ever shown by this class, so it is safe to call from a background thread
 * during application startup.</p>
 */
public final class UpdateService {

    private static final Logger logger = Logger.getLogger("com.openbravo.pos.updates.UpdateService");

    private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DOWNLOAD_URL = Pattern.compile(
            "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.exe)\"");

    private UpdateService() {
    }

    /**
     * Queries the update server. Returns null when there is no update or the
     * server cannot be reached (failures are logged, never thrown to the UI).
     */
    public static UpdateInfo check(AppConfig config) {
        String endpoint = UpdateSettings.getUrl(config);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "uniCenta oPOS Updater");
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                logger.log(Level.WARNING, "Update check failed with HTTP {0}", status);
                return null;
            }
            String json = readString(conn.getInputStream(), 262144);

            Matcher tag = TAG_NAME.matcher(json);
            if (!tag.find()) {
                logger.warning("Update response has no tag_name");
                return null;
            }
            String version = tag.group(1);

            String current = AppLocal.APP_VERSION;
            if (compareVersions(version, current) <= 0) {
                logger.log(Level.INFO, "No update available (server={0}, local={1})",
                        new Object[] {version, current});
                return null;
            }

            Matcher url = DOWNLOAD_URL.matcher(json);
            if (!url.find()) {
                logger.warning("Update response has no Windows installer asset");
                return null;
            }
            String downloadUrl = url.group(1);
            String assetName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
            logger.log(Level.INFO, "Update available: {0} -> {1}", new Object[] {version, assetName});
            return new UpdateInfo(version, assetName, downloadUrl);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Cannot check for updates at " + endpoint, e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Downloads the installer silently into the configured directory.
     *
     * @return the downloaded file, or null on failure
     */
    public static File download(AppConfig config, UpdateInfo info) {
        File dir = UpdateSettings.getDownloadDir(config);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            logger.log(Level.WARNING, "Cannot create update directory: {0}", dir);
            return null;
        }
        if (!info.getDownloadUrl().startsWith("https://")) {
            logger.warning("Refusing a non-HTTPS download URL");
            return null;
        }
        File target = new File(dir, info.getAssetName());
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(info.getDownloadUrl()).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "uniCenta oPOS Updater");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                logger.log(Level.WARNING, "Download failed with HTTP {0}", conn.getResponseCode());
                return null;
            }
            InputStream in = conn.getInputStream();
            OutputStream out = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
            } finally {
                out.close();
                in.close();
            }
            if (target.length() < 1024) {
                logger.log(Level.WARNING, "Download is too small: {0}", target);
                target.delete();
                return null;
            }
            // Remove older downloaded installers so a stale build can never
            // be installed later instead of the one just downloaded.
            File[] old = dir.listFiles();
            if (old != null) {
                for (File f : old) {
                    if (f.isFile() && !f.equals(target)
                            && f.getName().toLowerCase().endsWith(".exe")) {
                        f.delete();
                    }
                }
            }
            logger.log(Level.INFO, "Downloaded update to {0} ({1} bytes)",
                    new Object[] {target, target.length()});
            return target;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Cannot download update", e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Starts the downloaded installer with no user interaction at all.
     * Only called when update.autoInstall=true.
     */
    public static boolean installSilently(File installer) {
        if (installer == null || !installer.isFile()) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(installer.getAbsolutePath(),
                    "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/NOICONS");
            pb.directory(installer.getParentFile());
            Process process = pb.start();
            int exit = process.waitFor();
            logger.log(Level.INFO, "Silent installer exited with code {0}", exit);
            return exit == 0;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Cannot start silent installer", e);
            return false;
        }
    }

    /**
     * Builds a batch file that waits until this JVM has exited and then runs
     * the installer silently. Windows cannot overwrite the JARs while the POS
     * is running, so an immediate silent install always fails.
     */
    static File createScheduleScript(File installer, String pid, String appDir) throws Exception {
        File script = new File(installer.getParentFile(), "install-pending.cmd");
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");
        sb.append(":wait\r\n");
        sb.append("tasklist /FI \"PID eq ").append(pid).append("\" 2>NUL | find /I \"")
                .append(pid).append("\" >NUL\r\n");
        sb.append("if not errorlevel 1 (\r\n");
        sb.append("  ping -n 2 127.0.0.1 >NUL\r\n");
        sb.append("  goto wait\r\n");
        sb.append(")\r\n");
        sb.append("ping -n 2 127.0.0.1 >NUL\r\n");
        sb.append("start \"\" \"").append(installer.getAbsolutePath())
                .append("\" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /DIR=\"")
                .append(appDir).append("\"\r\n");
        sb.append("del \"%~f0\" >NUL 2>&1\r\n");
        FileOutputStream out = new FileOutputStream(script);
        try {
            out.write(sb.toString().getBytes("UTF-8"));
        } finally {
            out.close();
        }
        return script;
    }

    /**
     * Schedules the downloaded installer to run silently as soon as the POS
     * process exits, and returns immediately.
     */
    public static boolean scheduleInstallAfterExit(AppConfig config, File installer) {
        if (installer == null || !installer.isFile()) {
            return false;
        }
        try {
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            String appDir = new File(System.getProperty("dirname.path", ".")).getCanonicalPath();
            File script = createScheduleScript(installer, pid, appDir);
            new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "/B",
                    "\"" + script.getAbsolutePath() + "\"").start();
            logger.log(Level.INFO, "Installer scheduled to run after exit: {0}", installer);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Cannot schedule the silent installer", e);
            return false;
        }
    }

    /**
     * Returns the most recently downloaded installer, so the user can retry an
     * installation that failed (for example because the POS had files locked).
     */
    public static File findLatestDownload(AppConfig config) {
        File dir = UpdateSettings.getDownloadDir(config);
        File[] files = dir.listFiles();
        File latest = null;
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".exe")
                        && (latest == null || f.lastModified() > latest.lastModified())) {
                    latest = f;
                }
            }
        }
        return latest;
    }

    /** Starts a daemon thread that checks (and optionally downloads) silently. */
    public static void startSilentBackgroundCheck(final AppConfig config) {
        UpdateSettings.installDefaults(config);
        if (!UpdateSettings.isEnabled(config)) {
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        long last = parseTimestamp(config.getProperty(UpdateSettings.LAST_CHECK));
                        long interval = UpdateSettings.getIntervalHours(config) * 3600000L;
                        if (System.currentTimeMillis() - last >= interval) {
                            UpdateInfo info = check(config);
                            config.setProperty(UpdateSettings.LAST_CHECK,
                                    Long.toString(System.currentTimeMillis()));
                            if (info != null) {
                                File downloaded = download(config, info);
                                config.setProperty(UpdateSettings.LAST_VERSION, info.getVersion());
                                if (downloaded != null && UpdateSettings.isAutoInstall(config)) {
                                    scheduleInstallAfterExit(config, downloaded);
                                }
                            }
                            try {
                                config.save();
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Cannot persist update state", e);
                            }
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Silent update check failed", e);
                    }
                    try {
                        Thread.sleep(3600000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "unicenta-update-checker");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Compares two version strings numerically, ignoring the leading 'v' and
     * treating non numeric parts as lower than numeric ones.
     */
    public static int compareVersions(String left, String right) {
        String[] a = normalize(left).split("[.-]");
        String[] b = normalize(right).split("[.-]");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            String x = i < a.length ? a[i] : "0";
            String y = i < b.length ? b[i] : "0";
            int cmp;
            try {
                cmp = Integer.parseInt(x) - Integer.parseInt(y);
            } catch (NumberFormatException e) {
                cmp = x.compareToIgnoreCase(y);
            }
            if (cmp != 0) {
                return cmp < 0 ? -1 : 1;
            }
        }
        return 0;
    }

    private static String normalize(String version) {
        if (version == null) {
            return "";
        }
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        // Only the numeric prefix matters: "v3.81-hardened-1" and "3.81"
        // must compare equal, otherwise the same release is offered forever.
        StringBuilder numeric = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (Character.isDigit(ch) || ch == '.') {
                numeric.append(ch);
            } else {
                break;
            }
        }
        return numeric.length() == 0 ? v : numeric.toString();
    }

    private static long parseTimestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String readString(InputStream in, int limit) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1 && sb.length() < limit) {
            sb.append(new String(buffer, 0, n, "UTF-8"));
        }
        in.close();
        return sb.toString();
    }
}
