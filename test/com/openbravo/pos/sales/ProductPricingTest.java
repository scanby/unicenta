package com.openbravo.pos.sales;

import static org.junit.Assert.*;
import com.openbravo.pos.ticket.PriceBreakInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Tests for the quantity-based selling price tiers. */
public class ProductPricingTest {

    private static PriceBreakInfo tier(double qty, double price) {
        return new PriceBreakInfo("id" + qty, "product", qty, price);
    }

    @Test
    public void withoutTiersTheBasePriceIsUsed() {
        assertEquals(10.0, ProductPricing.getUnitPrice(10.0, 5, null), 0.001);
        assertEquals(10.0, ProductPricing.getUnitPrice(10.0, 5, new ArrayList<PriceBreakInfo>()), 0.001);
    }

    @Test
    public void anApplicableTierReplacesTheBasePrice() {
        List<PriceBreakInfo> tiers = Arrays.asList(tier(3, 9.0));
        assertEquals(10.0, ProductPricing.getUnitPrice(10.0, 2, tiers), 0.001);
        assertEquals(9.0, ProductPricing.getUnitPrice(10.0, 3, tiers), 0.001);
        assertEquals(9.0, ProductPricing.getUnitPrice(10.0, 10, tiers), 0.001);
    }

    @Test
    public void theLowestApplicablePriceWins() {
        List<PriceBreakInfo> tiers = Arrays.asList(tier(3, 9.0), tier(6, 8.0), tier(12, 7.5));
        assertEquals(9.0, ProductPricing.getUnitPrice(10.0, 4, tiers), 0.001);
        assertEquals(8.0, ProductPricing.getUnitPrice(10.0, 8, tiers), 0.001);
        assertEquals(7.5, ProductPricing.getUnitPrice(10.0, 20, tiers), 0.001);
    }

    @Test
    public void aTierNeverIncreasesThePrice() {
        List<PriceBreakInfo> tiers = Arrays.asList(tier(2, 12.0));
        assertEquals(10.0, ProductPricing.getUnitPrice(10.0, 5, tiers), 0.001);
    }

    @Test
    public void refundsUseTheAbsoluteQuantity() {
        List<PriceBreakInfo> tiers = Arrays.asList(tier(3, 9.0));
        assertEquals(9.0, ProductPricing.getUnitPrice(10.0, -4, tiers), 0.001);
    }

    @Test
    public void invalidTiersAreIgnored() {
        List<PriceBreakInfo> tiers = Arrays.asList(tier(0, 1.0), null, tier(2, 8.0));
        assertEquals(8.0, ProductPricing.getUnitPrice(10.0, 3, tiers), 0.001);
    }
}
