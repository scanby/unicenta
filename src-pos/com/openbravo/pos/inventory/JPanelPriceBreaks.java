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

package com.openbravo.pos.inventory;

import com.openbravo.basic.BasicException;
import com.openbravo.format.Formats;
import com.openbravo.pos.ticket.PriceBreakInfo;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Editor of the quantity price tiers of a product ("from N units -> price P").
 * Added as a tab of {@link ProductsEditor}.
 */
public class JPanelPriceBreaks extends JPanel {

    private final JCheckBox jchkActive = new JCheckBox("Aplicar precios por cantidad");
    private final JTextField jtxtQuantity = new JTextField(8);
    private final JTextField jtxtPrice = new JTextField(10);
    private final JButton jbtnAdd = new JButton("Anadir tramo");
    private final JButton jbtnRemove = new JButton("Quitar tramo");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Cantidad desde", "Precio de venta"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final List<PriceBreakInfo> breaks = new ArrayList<PriceBreakInfo>();

    public JPanelPriceBreaks() {
        initComponents();
        jchkActive.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setControlsEnabled(jchkActive.isSelected());
            }
        });
        setControlsEnabled(false);
    }

    private void initComponents() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel north = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 3, 3, 3);
        c.gridwidth = 4;
        north.add(jchkActive, c);

        c.gridy = 1;
        c.gridwidth = 1;
        north.add(new JLabel("Cantidad desde:"), c);
        c.gridx = 1;
        north.add(jtxtQuantity, c);
        c.gridx = 2;
        north.add(new JLabel("Precio venta:"), c);
        c.gridx = 3;
        north.add(jtxtPrice, c);

        c.gridy = 2;
        c.gridx = 0;
        north.add(jbtnAdd, c);
        c.gridx = 1;
        north.add(jbtnRemove, c);
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        jbtnAdd.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addBreak();
            }
        });
        jbtnRemove.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeBreak();
            }
        });
    }

    private void setControlsEnabled(boolean enabled) {
        jtxtQuantity.setEnabled(enabled);
        jtxtPrice.setEnabled(enabled);
        jbtnAdd.setEnabled(enabled);
        jbtnRemove.setEnabled(enabled);
        table.setEnabled(enabled);
    }

    private void addBreak() {
        try {
            Object qtyValue = Formats.DOUBLE.parseValue(jtxtQuantity.getText());
            Object priceValue = Formats.DOUBLE.parseValue(jtxtPrice.getText());
            if (qtyValue == null || priceValue == null) {
                return;
            }
            double qty = ((Number) qtyValue).doubleValue();
            double price = ((Number) priceValue).doubleValue();
            if (qty <= 0 || price < 0) {
                return;
            }
            breaks.add(new PriceBreakInfo(UUID.randomUUID().toString(), null, qty, price));
            refreshTable();
            jtxtQuantity.setText(null);
            jtxtPrice.setText(null);
        } catch (BasicException e) {
            // Invalid number: ignore the row.
        }
    }

    private void removeBreak() {
        int row = table.getSelectedRow();
        if (row >= 0 && row < breaks.size()) {
            breaks.remove(row);
            refreshTable();
        }
    }

    private void refreshTable() {
        model.setRowCount(0);
        for (PriceBreakInfo b : breaks) {
            model.addRow(new Object[] {
                Formats.DOUBLE.formatValue(b.getMinQuantity()),
                Formats.CURRENCY.formatValue(b.getPriceSell())
            });
        }
    }

    public boolean isActive() {
        return jchkActive.isSelected() && !breaks.isEmpty();
    }

    public void setActive(boolean active) {
        jchkActive.setSelected(active);
        setControlsEnabled(active);
    }

    public List<PriceBreakInfo> getBreaks() {
        return new ArrayList<PriceBreakInfo>(breaks);
    }

    public void setBreaks(List<PriceBreakInfo> list) {
        breaks.clear();
        if (list != null) {
            breaks.addAll(list);
        }
        jchkActive.setSelected(!breaks.isEmpty());
        setControlsEnabled(!breaks.isEmpty());
        refreshTable();
    }

    public void clear() {
        setBreaks(null);
    }
}
