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

package com.openbravo.pos.ticket;

import com.openbravo.data.loader.DataRead;
import com.openbravo.data.loader.Datas;
import com.openbravo.data.loader.SerializerRead;
import com.openbravo.data.loader.SerializableRead;
import com.openbravo.basic.BasicException;

/**
 * A quantity-based price tier for a product ("from N units, sell at price P").
 */
public class PriceBreakInfo implements SerializableRead {

    private String id;
    private String product;
    private double minQuantity;
    private double priceSell;

    public PriceBreakInfo() {
    }

    public PriceBreakInfo(String id, String product, double minQuantity, double priceSell) {
        this.id = id;
        this.product = product;
        this.minQuantity = minQuantity;
        this.priceSell = priceSell;
    }

    @Override
    public void readValues(DataRead dr) throws BasicException {
        id = dr.getString(1);
        product = dr.getString(2);
        minQuantity = dr.getDouble(3);
        priceSell = dr.getDouble(4);
    }

    public String getId() {
        return id;
    }

    public String getProduct() {
        return product;
    }

    public double getMinQuantity() {
        return minQuantity;
    }

    public double getPriceSell() {
        return priceSell;
    }

    public void setMinQuantity(double minQuantity) {
        this.minQuantity = minQuantity;
    }

    public void setPriceSell(double priceSell) {
        this.priceSell = priceSell;
    }

    public static SerializerRead getSerializerRead() {
        return new SerializerRead() {
            @Override
            public Object readValues(DataRead dr) throws BasicException {
                PriceBreakInfo info = new PriceBreakInfo();
                info.readValues(dr);
                return info;
            }
        };
    }

    public static final Datas[] FIELDS = new Datas[] {
        Datas.STRING, Datas.STRING, Datas.DOUBLE, Datas.DOUBLE
    };

    @Override
    public String toString() {
        return ">= " + minQuantity + " -> " + priceSell;
    }
}
