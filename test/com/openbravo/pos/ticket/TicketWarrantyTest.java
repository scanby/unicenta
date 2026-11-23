package com.openbravo.pos.ticket;

import static org.junit.Assert.*;
import java.util.Properties;
import org.junit.Test;

/**
 * Tests for the warranty detection extracted from JPanelTicket.
 */
public class TicketWarrantyTest {

    private static TicketLineInfo line(boolean warranty) {
        Properties props = new Properties();
        if (warranty) {
            props.setProperty("product.warranty", "true");
        }
        return new TicketLineInfo("p", 1, 1, null, props);
    }

    @Test
    public void emptyTicketHasNoWarranty() {
        assertFalse(TicketWarranty.containsWarranty(null));
        assertFalse(TicketWarranty.containsWarranty(new TicketInfo()));
    }

    @Test
    public void detectsWarrantyInFirstLine() {
        TicketInfo ticket = new TicketInfo();
        ticket.addLine(line(true));
        assertTrue(TicketWarranty.containsWarranty(ticket));
    }

    @Test
    public void detectsWarrantyInLaterLines() {
        // Regression: the old JPanelTicket loop returned after the first line.
        TicketInfo ticket = new TicketInfo();
        ticket.addLine(line(false));
        ticket.addLine(line(false));
        ticket.addLine(line(true));
        assertTrue(TicketWarranty.containsWarranty(ticket));
    }

    @Test
    public void noWarrantyWhenNoLineHasTheAttribute() {
        TicketInfo ticket = new TicketInfo();
        ticket.addLine(line(false));
        ticket.addLine(line(false));
        assertFalse(TicketWarranty.containsWarranty(ticket));
    }
}
