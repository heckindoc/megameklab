/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMekLab.
 *
 * MegaMekLab is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MegaMekLab is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 *
 * MechWarrior Copyright Microsoft Corporation. MegaMekLab was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package megameklab.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import megamek.client.ratgenerator.FactionRecord;
import megamek.client.ratgenerator.RATGenerator;
import megamek.client.ui.util.UIUtil;

/**
 * Picks the factions that field a unit. Multi-select, because "list the factions I want" is one step, not one trip
 * through a dropdown per faction.
 * <p>
 * There are over 400 faction files, which sounds unmanageable. It is not: the unit's introduction year is already known
 * by the time a player reaches the Availability tab, and filtering to the factions that actually exist in that year
 * leaves about 45. That also stops a whole class of mistake, since a faction that does not exist in the unit's year
 * cannot be picked at all. Clan Wolf simply does not appear for a 3150 unit.
 * </p>
 */
public class AddFactionsDialog extends JDialog {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DIALOG_WIDTH = 420;
    private static final int DIALOG_HEIGHT = 560;
    private static final int GROUP_COLUMNS = 1;

    /**
     * The umbrella keys. They are not factions, so they get their own block rather than being buried in an
     * alphabetical list of them. Each one reaches every faction that falls back to it.
     */
    private static final Map<String, String> UMBRELLA_KEYS = new LinkedHashMap<>();

    static {
        UMBRELLA_KEYS.put("General", "Everybody");
        UMBRELLA_KEYS.put("IS", "All Inner Sphere");
        UMBRELLA_KEYS.put("CLAN", "All Clans");
        UMBRELLA_KEYS.put("Periphery", "All Periphery");
    }

    /** The umbrella keys, for callers that need to know a code is one of them rather than a real faction. */
    public static final Set<String> UMBRELLA_CODES = Set.copyOf(UMBRELLA_KEYS.keySet());

    private final int year;
    private final List<String> alreadyChosen;

    private final JTextField filterField = new JTextField();
    private final JCheckBox showMinorCheckBox = new JCheckBox("Show minor factions");
    private final JPanel factionListPanel = new JPanel();
    private final JButton addButton = new JButton("Add");

    private final Map<String, JCheckBox> factionCheckBoxes = new LinkedHashMap<>();
    private final Map<String, JCheckBox> umbrellaCheckBoxes = new LinkedHashMap<>();

    private List<String> chosenFactionCodes = List.of();

    /**
     * @param parent        the window to sit over
     * @param year          the unit's introduction year, which decides which factions exist
     * @param alreadyChosen faction codes already in the table, shown ticked and disabled
     */
    public AddFactionsDialog(Component parent, int year, List<String> alreadyChosen) {
        super((Dialog) null, "Add factions", true);
        this.year = year;
        this.alreadyChosen = List.copyOf(alreadyChosen);

        buildLayout();
        populateFactions();

        setSize(UIUtil.scaleForGUI(DIALOG_WIDTH, DIALOG_HEIGHT));
        setLocationRelativeTo(parent);
    }

    /**
     * The faction codes the player ticked. Empty if they cancelled.
     *
     * @return the chosen codes
     */
    public List<String> getChosenFactionCodes() {
        return chosenFactionCodes;
    }

    private void buildLayout() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        JPanel filterPanel = new JPanel(new BorderLayout(8, 0));
        filterPanel.add(new JLabel("Filter:"), BorderLayout.WEST);
        filterPanel.add(filterField, BorderLayout.CENTER);
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                applyFilter();
            }
        });
        topPanel.add(filterPanel);

        JPanel umbrellaPanel = new JPanel(new GridLayout(0, GROUP_COLUMNS));
        umbrellaPanel.setBorder(BorderFactory.createTitledBorder("Groups"));
        for (Map.Entry<String, String> umbrella : UMBRELLA_KEYS.entrySet()) {
            JCheckBox checkBox = new JCheckBox(umbrella.getValue() + " (" + umbrella.getKey() + ")");
            checkBox.setEnabled(!alreadyChosen.contains(umbrella.getKey()));
            checkBox.setSelected(alreadyChosen.contains(umbrella.getKey()));
            umbrellaCheckBoxes.put(umbrella.getKey(), checkBox);
            umbrellaPanel.add(checkBox);
        }
        topPanel.add(umbrellaPanel);

        JPanel minorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        minorPanel.add(new JLabel("Factions that exist in " + year));
        minorPanel.add(showMinorCheckBox);
        showMinorCheckBox.addActionListener(event -> populateFactions());
        topPanel.add(minorPanel);

        add(topPanel, BorderLayout.NORTH);

        factionListPanel.setLayout(new BoxLayout(factionListPanel, BoxLayout.Y_AXIS));
        add(new JScrollPane(factionListPanel), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(event -> dispose());
        addButton.addActionListener(event -> confirm());
        buttonPanel.add(cancelButton);
        buttonPanel.add(addButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Fills the list with the factions that exist in the unit's year. Top-level factions only: a command such as
     * FS.CMB is a faction key too, but commands are the long tail and are not offered yet.
     */
    private void populateFactions() {
        factionListPanel.removeAll();
        factionCheckBoxes.clear();

        RATGenerator ratGenerator = RATGenerator.getInstance();
        if (!ratGenerator.isInitialized()) {
            factionListPanel.add(new JLabel("Loading factions..."));
            factionListPanel.revalidate();
            factionListPanel.repaint();
            return;
        }

        List<FactionRecord> factions = new ArrayList<>();
        for (FactionRecord factionRecord : ratGenerator.getFactionList()) {
            if (factionRecord.getKey().contains(".")) {
                // A command, not a faction. The long tail; not offered yet.
                continue;
            }
            if (factionRecord.isMinor() && !showMinorCheckBox.isSelected()) {
                continue;
            }
            if (!factionRecord.isActiveInYear(year)) {
                continue;
            }
            factions.add(factionRecord);
        }

        factions.sort(Comparator.comparing(factionRecord -> factionRecord.getName(year)));

        for (FactionRecord factionRecord : factions) {
            String code = factionRecord.getKey();
            JCheckBox checkBox = new JCheckBox(factionRecord.getName(year) + " (" + code + ")");
            checkBox.setEnabled(!alreadyChosen.contains(code));
            checkBox.setSelected(alreadyChosen.contains(code));
            factionCheckBoxes.put(code, checkBox);
            factionListPanel.add(checkBox);
        }

        applyFilter();
        factionListPanel.revalidate();
        factionListPanel.repaint();
    }

    private void applyFilter() {
        String filter = filterField.getText().trim().toLowerCase();

        for (Map.Entry<String, JCheckBox> entry : factionCheckBoxes.entrySet()) {
            JCheckBox checkBox = entry.getValue();
            boolean matches = filter.isEmpty()
                  || checkBox.getText().toLowerCase().contains(filter);
            checkBox.setVisible(matches);
        }

        factionListPanel.revalidate();
        factionListPanel.repaint();
    }

    private void confirm() {
        List<String> chosen = new ArrayList<>();

        for (Map.Entry<String, JCheckBox> entry : umbrellaCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected() && !alreadyChosen.contains(entry.getKey())) {
                chosen.add(entry.getKey());
            }
        }
        for (Map.Entry<String, JCheckBox> entry : factionCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected() && !alreadyChosen.contains(entry.getKey())) {
                chosen.add(entry.getKey());
            }
        }

        chosenFactionCodes = chosen;
        dispose();
    }
}
