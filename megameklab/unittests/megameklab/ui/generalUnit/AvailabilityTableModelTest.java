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
package megameklab.ui.generalUnit;

import static megamek.common.units.ForceGeneratorAvailability.UNSPECIFIED_YEAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import megamek.common.units.ForceGeneratorAvailability;
import megameklab.ui.generalUnit.AvailabilityTableModel.AvailabilityRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvailabilityTableModelTest {

    /** In the tab this maps a code to a real faction name; the table's behaviour does not depend on it. */
    private static final Function<String, String> NAMER = code -> "Name of " + code;

    private AvailabilityTableModel model;

    @BeforeEach
    void setUp() {
        model = new AvailabilityTableModel();
    }

    @Test
    void loadSplitsEachFactionInAnEntryIntoItsOwnRow() {
        model.loadFrom(List.of(ForceGeneratorAvailability.parse("FS:5,LA:3")), NAMER);

        assertEquals(2, model.getRowCount());
        assertEquals("FS", model.getRow(0).factionCode());
        assertEquals(5, model.getRow(0).availability());
        assertEquals("LA", model.getRow(1).factionCode());
        assertEquals(3, model.getRow(1).availability());
    }

    @Test
    void loadKeepsTheYearRange() {
        model.loadFrom(List.of(ForceGeneratorAvailability.parse("3067-3085 FS:7")), NAMER);

        assertEquals(3067, model.getRow(0).fromYear());
        assertEquals(3085, model.getRow(0).toYear());
    }

    @Test
    void aFileWrittenByHandSurvivesBeingOpened() {
        // The tab does not offer the +/- suffixes, but a hand-edited file may carry them. Opening such a file must
        // not silently mangle the number.
        model.loadFrom(List.of(ForceGeneratorAvailability.parse("CJF:5+,CSA:2-")), NAMER);

        assertEquals(5, model.getRow(0).availability());
        assertEquals(2, model.getRow(1).availability());
    }

    @Test
    void roundTripsThroughTheFileFormat() {
        List<ForceGeneratorAvailability> original = List.of(
              ForceGeneratorAvailability.parse("FS:5,LA:3"),
              ForceGeneratorAvailability.parse("3067-3085 FS:7"));

        model.loadFrom(original, NAMER);
        List<ForceGeneratorAvailability> written = model.toAvailabilityEntries();

        assertEquals(2, written.size());
        assertEquals("FS:5,LA:3", written.get(0).availabilityCodes());
        assertEquals(UNSPECIFIED_YEAR, written.get(0).startYear());
        assertEquals("FS:7", written.get(1).availabilityCodes());
        assertEquals(3067, written.get(1).startYear());
        assertEquals(3085, written.get(1).endYear());
    }

    @Test
    void factionsSharingAYearRangeAreWrittenAsOneLine() {
        // A hand-written file would put them on one line, so the tab should too
        model.addRow(new AvailabilityRow("FS", "Federated Suns", 5, UNSPECIFIED_YEAR, UNSPECIFIED_YEAR, false));
        model.addRow(new AvailabilityRow("LA", "Lyran Commonwealth", 3, UNSPECIFIED_YEAR, UNSPECIFIED_YEAR, false));
        model.addRow(new AvailabilityRow("CJF", "Clan Jade Falcon", 7, 3067, 3085, false));

        List<ForceGeneratorAvailability> written = model.toAvailabilityEntries();

        assertEquals(2, written.size());
        assertEquals("FS:5,LA:3", written.get(0).availabilityCodes());
        assertEquals("CJF:7", written.get(1).availabilityCodes());
    }

    @Test
    void addingAFactionTwiceOnlyAddsItOnce() {
        int first = model.addRow(new AvailabilityRow("FS", "Federated Suns", 5, UNSPECIFIED_YEAR, UNSPECIFIED_YEAR,
              false));
        int second = model.addRow(new AvailabilityRow("FS", "Federated Suns", 9, UNSPECIFIED_YEAR, UNSPECIFIED_YEAR,
              false));

        assertEquals(first, second);
        assertEquals(1, model.getRowCount());
        // The first value stands; the player edits it with the slider rather than by adding the faction again
        assertEquals(5, model.getRow(0).availability());
    }

    @Test
    void factionsThatDoNotExistInTheUnitsYearAreFlagged() {
        // Clan Wolf is a real choice for a 3050 unit and a dead one for a 3150 unit. Changing the intro year on Basic
        // Info after filling this tab in must not leave the player with a silently dead row.
        model.addRow(new AvailabilityRow("CW", "Clan Wolf", 5, UNSPECIFIED_YEAR, UNSPECIFIED_YEAR, false));
        model.addRow(new AvailabilityRow("CWE", "Wolf Empire", 5, UNSPECIFIED_YEAR, UNSPECIFIED_YEAR, false));

        model.markStaleFactions(Set.of("CWE", "FS", "LA"));

        assertTrue(model.getRow(0).stale());
        assertFalse(model.getRow(1).stale());
        assertTrue(model.hasStaleRows());
    }

    @Test
    void anEmptyTableWritesNothing() {
        assertTrue(model.toAvailabilityEntries().isEmpty());
    }
}
