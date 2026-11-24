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

package com.openbravo.pos.sales;

import com.openbravo.pos.ticket.PriceBreakInfo;
import java.util.List;

/**
 * Computes the effective unit selling price from the quantity-based price
 * tiers configured for a product.
 *
 * <p>Each tier means "from N units, sell at price P". When several tiers
 * apply to the same quantity, the lowest price wins. The base price is always
 * a ceiling: a tier can only reduce it, never increase it.</p>
 */
public final class ProductPricing {

    private ProductPricing() {
    }

    /**
     * @param basePrice regular selling price of the product
     * @param quantity  units being sold (negative quantities are treated as absolute)
     * @param breaks    configured tiers (may be null or empty)
     * @return the price to apply to every unit of the line
     */
    public static double getUnitPrice(double basePrice, double quantity, List<PriceBreakInfo> breaks) {
        if (breaks == null || breaks.isEmpty()) {
            return basePrice;
        }
        double units = Math.abs(quantity);
        double best = basePrice;
        for (PriceBreakInfo b : breaks) {
            if (b == null || b.getMinQuantity() <= 0) {
                continue;
            }
            if (units >= b.getMinQuantity() && b.getPriceSell() < best) {
                best = b.getPriceSell();
            }
        }
        return best;
    }
}
