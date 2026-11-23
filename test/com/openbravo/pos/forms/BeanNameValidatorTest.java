package com.openbravo.pos.forms;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Bean names must never be able to instantiate arbitrary classes.
 */
public class BeanNameValidatorTest {

    @Test
    public void acceptsApplicationClasses() {
        BeanNameValidator.validate("com.openbravo.pos.forms.DataLogicSales");
        BeanNameValidator.validate("com.openbravo.pos.inventory.ProductsPanel");
    }

    @Test
    public void acceptsVersionedScriptResources() {
        BeanNameValidator.validate("/com/openbravo/reports/customers.bs");
    }

    @Test
    public void rejectsForeignClasses() {
        assertThrowsBean("java.lang.Runtime");
        assertThrowsBean("javax.script.ScriptEngineManager");
        assertThrowsBean("org.apache.commons.collections.functors.InvokerTransformer");
    }

    @Test
    public void rejectsMalformedNames() {
        assertThrowsBean(null);
        assertThrowsBean("");
        assertThrowsBean("com.openbravo.pos.forms.DataLogicSales;calc");
        assertThrowsBean("/com/openbravo/reports/../../evil.bs");
        assertThrowsBean("/com/openbravo/reports/customers.java");
    }

    private static void assertThrowsBean(String name) {
        try {
            BeanNameValidator.validate(name);
            fail("Expected BeanFactoryException for: " + name);
        } catch (BeanFactoryException expected) {
            // ok
        }
    }
}
