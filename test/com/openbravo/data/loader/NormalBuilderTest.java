package com.openbravo.data.loader;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Regression tests for the ? placeholder substitution in StaticSentence.
 */
public class NormalBuilderTest {

    @Test
    public void placeholderAtPositionZeroIsReplaced() throws Exception {
        // Regression: the old loop used indexOf('?') > 0 and skipped position 0.
        assertEquals("NULL", new NormalBuilder("?").getSQL(null, null).trim());
    }

    @Test
    public void everyPlaceholderIsReplaced() throws Exception {
        assertEquals("SELECT NULL FROM NULL",
                new NormalBuilder("SELECT ? FROM ?").getSQL(null, null));
    }

    @Test
    public void valuesAreEscapedWhenWritten() throws Exception {
        SerializerWrite sw = new SerializerWriteBasic(Datas.STRING);
        String sql = new NormalBuilder("VALUES (?)").getSQL(sw, new Object[] {"O'Brien"});
        assertEquals("VALUES ('O''Brien')", sql);
    }
}
