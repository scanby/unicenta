package com.openbravo.data.loader;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests for SQL literal generation used by StaticSentence, NormalBuilder and QBFBuilder.
 */
public class DataWriteUtilsTest {

    @Test
    public void quotesAreDoubledUsingSqlStandard() {
        assertEquals("'O''Brien'", DataWriteUtils.getSQLValue("O'Brien"));
        assertEquals("''''", DataWriteUtils.getSQLValue("'"));
        assertEquals("a''b", DataWriteUtils.getEscaped("a'b"));
    }

    @Test
    public void injectedQuoteCannotCloseTheLiteral() {
        String value = "x' OR '1'='1";
        String sql = DataWriteUtils.getSQLValue(value);
        assertEquals("'x'' OR ''1''=''1'", sql);
        // The literal starts and ends with one quote; every inner quote is doubled.
        assertTrue(sql.startsWith("'x''"));
        assertTrue(sql.endsWith("''1'"));
    }

    @Test
    public void nullBecomesSqlNull() {
        assertEquals("NULL", DataWriteUtils.getSQLValue((Object) null));
        assertEquals("NULL", DataWriteUtils.getSQLValue((String) null));
    }

    @Test
    public void otherEscapesRemainBackwardsCompatible() {
        assertEquals("a\\\\b", DataWriteUtils.getEscaped("a\\b"));
        assertEquals("a\\nb", DataWriteUtils.getEscaped("a\nb"));
        assertEquals("a\\rb", DataWriteUtils.getEscaped("a\rb"));
    }
}
