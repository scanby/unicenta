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
//    along with uniCenta oPOS.  If not, see <http://www.gnu.org/licenses/>.

package com.openbravo.pos.instance;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 * @author adrianromero
 */
public interface AppMessage extends Remote {
    
    /**
     * Restores the running window, but only for callers that know the local
     * per-user token.
     *
     * @param token secret read from the user's private token file
     * @return true if the window was actually restored
     * @throws RemoteException
     */
    public boolean restoreWindow(String token) throws RemoteException;

}
