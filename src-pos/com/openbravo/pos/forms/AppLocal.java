//    uniCenta oPOS  - Touch Friendly Point Of Sale
//    Copyright (c) 2009-2014 uniCenta & previous Openbravo POS works
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
//    along with uniCenta oPOS.  If not, see <http://www.gnu.org/licenses/>

package com.openbravo.pos.forms;

import com.openbravo.beans.LocaleResources;

/**
 *
 * @author adrianromero
 */
public class AppLocal {
    
    /**
     *
     */
    public static final String APP_NAME = "uniCenta oPOS";

    /**
     *
     */
    public static final String APP_ID = "unicentaopos";

    /**
     * Database schema version. Only changes when a SQL upgrade script is added;
     * used by JRootApp to select <DB>-create.sql / <DB>-upgrade-<version>.sql.
     */
    public static final String APP_VERSION = "3.82";

    /**
     * Build version, incremented on every code change. Shown in window titles,
     * the customer display, the installer and the release tag so it is always
     * clear which build is being tested.
     */
    public static final String APP_BUILD = "3.82.1";

    private static final LocaleResources m_resources;
    
    static {
        m_resources = new LocaleResources();
        m_resources.addBundleName("pos_messages");
        m_resources.addBundleName("erp_messages");
    }
    
    /** Creates a new instance of AppLocal */
    private AppLocal() {
    }
    
    /**
     *
     * @param sKey
     * @return
     */
    public static String getIntString(String sKey) {
        return m_resources.getString(sKey);
    }
    
    /**
     *
     * @param sKey
     * @param sValues
     * @return
     */
    public static String getIntString(String sKey, Object ... sValues) {
        return m_resources.getString(sKey, sValues);
    }
}