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

package com.openbravo.pos.forms;

/**
 * Validates bean names before they are passed to Class.forName() or to the
 * BeanShell bean factory. Names originally came from a menu script in the
 * database, which made them an arbitrary-class-instantiation vector.
 */
public final class BeanNameValidator {

    private BeanNameValidator() {
    }

    /**
     * @param name bean name to validate
     * @throws BeanFactoryException when the name is outside the allowed set
     */
    public static void validate(String name) throws BeanFactoryException {
        if (name == null) {
            throw new BeanFactoryException("Null bean name");
        }
        if (name.startsWith("/")) {
            if (name.contains("..") || !name.matches("/[A-Za-z0-9_./-]+\\.bs")) {
                throw new BeanFactoryException("Invalid script bean name: " + name);
            }
            return;
        }
        if (!name.matches("[A-Za-z_$][A-Za-z0-9_$.]*")
                || !(name.startsWith("com.openbravo.")
                     || name.startsWith("net.sf.jasperreports."))) {
            throw new BeanFactoryException("Bean name is not in an allowed package: " + name);
        }
    }
}
