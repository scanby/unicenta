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

/** Description of a release published by the update server. */
public final class UpdateInfo {

    private final String version;
    private final String assetName;
    private final String downloadUrl;

    public UpdateInfo(String version, String assetName, String downloadUrl) {
        this.version = version;
        this.assetName = assetName;
        this.downloadUrl = downloadUrl;
    }

    public String getVersion() {
        return version;
    }

    public String getAssetName() {
        return assetName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    @Override
    public String toString() {
        return version + " (" + assetName + ")";
    }
}
