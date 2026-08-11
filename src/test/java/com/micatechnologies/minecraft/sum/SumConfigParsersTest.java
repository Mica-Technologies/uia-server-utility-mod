package com.micatechnologies.minecraft.sum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.sum.api.EconomyScope;
import com.micatechnologies.minecraft.sum.border.BorderEntry;
import com.micatechnologies.minecraft.sum.loyalty.LoyaltyMilestone;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests the config string-array parsers in {@link SumConfig} that turn Forge's
 * {@code "dimId=radius:mode"} (border) and {@code "minutes=type:value"} (loyalty) string
 * lists into typed objects. These formats are exactly the fiddly, easily-broken kind of
 * parsing that benefits from unit coverage (TESTING_PLAN.md §1.10 / §4.8 M4 + M6).
 *
 * <p>The parsers are private static and only reference {@code Sum.LOGGER} (a plain log4j
 * logger) for warnings, so they invoke cleanly via reflection with no Minecraft runtime.
 */
class SumConfigParsersTest {

    @SuppressWarnings("unchecked")
    private static Map<Integer, BorderEntry> parseBorders(String... entries) throws Exception {
        Method m = SumConfig.class.getDeclaredMethod("parseBorderEntries", String[].class);
        m.setAccessible(true);
        return (Map<Integer, BorderEntry>) m.invoke(null, (Object) entries);
    }

    @SuppressWarnings("unchecked")
    private static List<LoyaltyMilestone> parseMilestones(String... entries) throws Exception {
        Method m = SumConfig.class.getDeclaredMethod("parseLoyaltyMilestones", String[].class, String.class);
        m.setAccessible(true);
        return (List<LoyaltyMilestone>) m.invoke(null, (Object) entries, "test");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<EconomyScope>> parseAllowedMods(String... entries)
        throws Exception {
        Method m = SumConfig.class.getDeclaredMethod("parseAllowedMods", String[].class);
        m.setAccessible(true);
        return (Map<String, Set<EconomyScope>>) m.invoke(null, (Object) entries);
    }

    // --- Border parser ---

    @Test
    void parsesValidBorderEntry() throws Exception {
        Map<Integer, BorderEntry> result = parseBorders("0=1000:bounce");
        assertEquals(1, result.size());
        BorderEntry e = result.get(0);
        assertEquals(0, e.getDimId());
        assertEquals(1000.0, e.getRadius());
        assertSame(BorderEntry.Mode.BOUNCE, e.getMode());
    }

    @Test
    void borderModeAndDimParsingIsLenient() throws Exception {
        // negative dim, decimal radius, uppercase mode, surrounding whitespace
        Map<Integer, BorderEntry> result = parseBorders("  -1 = 512.5 : LOOP  ");
        assertEquals(1, result.size());
        BorderEntry e = result.get(-1);
        assertEquals(512.5, e.getRadius());
        assertSame(BorderEntry.Mode.LOOP, e.getMode());
    }

    @Test
    void borderParserSkipsMalformedEntries() throws Exception {
        Map<Integer, BorderEntry> result = parseBorders(
            "",                  // blank
            "garbage",           // no '='
            "=1000:bounce",      // empty dim
            "x=1000:bounce",     // non-int dim
            "0=abc:bounce",      // non-number radius
            "0=1000",            // missing ':mode'
            "0=-5:bounce",       // non-positive radius
            "0=1000:teleport");  // unknown mode
        assertTrue(result.isEmpty(), "all entries were malformed; result should be empty");
    }

    @Test
    void borderParserKeepsFirstOnDuplicateDimension() throws Exception {
        Map<Integer, BorderEntry> result = parseBorders("0=1000:bounce", "0=2000:loop");
        assertEquals(1, result.size());
        assertEquals(1000.0, result.get(0).getRadius(), "first entry wins on duplicate dim");
        assertSame(BorderEntry.Mode.BOUNCE, result.get(0).getMode());
    }

    @Test
    void borderParserHandlesMultipleDimensions() throws Exception {
        Map<Integer, BorderEntry> result = parseBorders("0=1000:bounce", "-1=200:loop", "1=64:bounce");
        assertEquals(3, result.size());
        assertEquals(1000.0, result.get(0).getRadius());
        assertEquals(200.0, result.get(-1).getRadius());
        assertEquals(64.0, result.get(1).getRadius());
    }

    // --- Loyalty milestone parser ---

    @Test
    void parsesMoneyMilestone() throws Exception {
        List<LoyaltyMilestone> result = parseMilestones("60=money:100");
        assertEquals(1, result.size());
        LoyaltyMilestone m = result.get(0);
        assertEquals(60, m.getMinutes());
        assertSame(LoyaltyMilestone.Type.MONEY, m.getType());
        assertEquals("100", m.getValue());
        assertEquals(60 * 20 * 60, m.getTicks());
    }

    @Test
    void parsesCommandMilestoneWithColonsInValue() throws Exception {
        // The value may itself contain ':' (commands often do) — only the first ':' splits.
        List<LoyaltyMilestone> result = parseMilestones("120=command:give @p minecraft:diamond 1");
        assertEquals(1, result.size());
        LoyaltyMilestone m = result.get(0);
        assertEquals(120, m.getMinutes());
        assertSame(LoyaltyMilestone.Type.COMMAND, m.getType());
        assertEquals("give @p minecraft:diamond 1", m.getValue());
    }

    @Test
    void loyaltyParserSkipsMalformedEntries() throws Exception {
        List<LoyaltyMilestone> result = parseMilestones(
            "",                 // blank
            "money:100",        // no '='
            "=money:100",       // empty minutes
            "abc=money:100",    // non-int minutes
            "0=money:100",      // non-positive minutes
            "-5=money:100",     // negative minutes
            "60=bogus:1",       // unknown type
            "60=money");        // missing ':value'
        assertTrue(result.isEmpty());
    }

    @Test
    void loyaltyParserKeepsFirstOnDuplicateMinutes() throws Exception {
        List<LoyaltyMilestone> result = parseMilestones("60=money:100", "60=command:say hi");
        assertEquals(1, result.size());
        assertSame(LoyaltyMilestone.Type.MONEY, result.get(0).getType(), "first milestone wins");
        assertEquals("100", result.get(0).getValue());
    }

    @Test
    void loyaltyParserPreservesOrderAcrossDistinctMinutes() throws Exception {
        List<LoyaltyMilestone> result = parseMilestones("30=money:50", "60=money:100", "90=command:say gz");
        assertEquals(3, result.size());
        assertEquals(30, result.get(0).getMinutes());
        assertEquals(60, result.get(1).getMinutes());
        assertEquals(90, result.get(2).getMinutes());
    }

    @Test
    void emptyInputProducesEmptyResults() throws Exception {
        assertTrue(parseBorders().isEmpty());
        assertTrue(parseMilestones().isEmpty());
        // And a lone null-ish blank list element doesn't crash.
        assertNull(parseBorders("").get(0));
    }

    // --- RoadRunner speed-block parser (block=multiplier) ---

    @Test
    void parsesValidSpeedBlock() {
        Map<String, Double> result = SumConfig.parseSpeedBlocks(new String[] {"minecraft:concrete=1.8"});
        assertEquals(1, result.size());
        assertEquals(1.8, result.get("minecraft:concrete"));
    }

    @Test
    void speedBlockParserTrimsWhitespace() {
        Map<String, Double> result = SumConfig.parseSpeedBlocks(new String[] {"  minecraft:stone = 2.0 "});
        assertEquals(2.0, result.get("minecraft:stone"));
    }

    @Test
    void speedBlockParserSkipsMalformedEntries() {
        Map<String, Double> result = SumConfig.parseSpeedBlocks(new String[] {
            "",                    // blank → no '='
            "garbage",             // no '='
            "minecraft:stone=abc", // non-numeric multiplier
            "a=b=1.5"});           // split("=",2) → parse "b=1.5" fails
        assertTrue(result.isEmpty(), "every entry was malformed");
    }

    @Test
    void speedBlockParserEmptyInputIsEmpty() {
        assertTrue(SumConfig.parseSpeedBlocks(new String[0]).isEmpty());
    }

    // --- Economy integration allowlist parser ---
    //
    // This one decides which other mods may move players' money, so its failure modes matter more
    // than the others': a line that silently grants too much is a mod spending balances nobody
    // authorized, and a line that silently grants nothing is a working integration that mystifies
    // an operator by refusing every call.

    @Test
    void allowlistParsesModAndScopes() throws Exception {
        Map<String, Set<EconomyScope>> result = parseAllowedMods("mycasino=wallet_read,bank_read");
        assertEquals(1, result.size());
        assertEquals(EnumSet.of(EconomyScope.WALLET_READ, EconomyScope.BANK_READ),
            result.get("mycasino"));
    }

    @Test
    void allowlistExpandsImpliedScopes() throws Exception {
        Map<String, Set<EconomyScope>> result = parseAllowedMods("mycasino=escrow");
        assertEquals(EnumSet.of(EconomyScope.ESCROW, EconomyScope.WALLET_WRITE,
            EconomyScope.WALLET_READ), result.get("mycasino"),
            "escrow debits and refunds a wallet, so it must carry the wallet scopes");
    }

    @Test
    void allowlistWildcardGrantsEverything() throws Exception {
        assertEquals(EnumSet.allOf(EconomyScope.class), parseAllowedMods("mycasino=*")
            .get("mycasino"));
    }

    @Test
    void allowlistIsCaseAndWhitespaceInsensitive() throws Exception {
        Map<String, Set<EconomyScope>> result =
            parseAllowedMods("  MyCasino =  WALLET_WRITE , bank_read  ");
        assertEquals(EnumSet.of(EconomyScope.WALLET_WRITE, EconomyScope.WALLET_READ,
            EconomyScope.BANK_READ), result.get("mycasino"),
            "operators type this by hand; the mod id is matched lower-case");
    }

    @Test
    void allowlistKeepsValidScopesWhenOneIsUnknown() throws Exception {
        Map<String, Set<EconomyScope>> result =
            parseAllowedMods("mycasino=wallet_read,teleport,bank_read");
        assertEquals(EnumSet.of(EconomyScope.WALLET_READ, EconomyScope.BANK_READ),
            result.get("mycasino"), "one bad token must not discard the rest of the line");
    }

    @Test
    void allowlistDropsEntriesGrantingNothing() throws Exception {
        Map<String, Set<EconomyScope>> result = parseAllowedMods(
            "mycasino=",           // no scopes at all
            "othermod=teleport",   // only an unknown scope
            "thirdmod= , ,");      // only separators
        assertTrue(result.isEmpty(),
            "an authorized mod with no scopes would report as allowed while failing every call");
    }

    @Test
    void allowlistSkipsMalformedEntries() throws Exception {
        Map<String, Set<EconomyScope>> result = parseAllowedMods(
            "",                       // blank
            "   ",                    // whitespace
            "# mycasino=wallet_read", // commented out
            "garbage",                // no '='
            "=wallet_read",           // no mod id
            null);                    // null element
        assertTrue(result.isEmpty(), "malformed lines are skipped, never fatal");
    }

    @Test
    void allowlistLastEntryWinsOnDuplicateModId() throws Exception {
        Map<String, Set<EconomyScope>> result =
            parseAllowedMods("mycasino=wallet_read", "mycasino=bank_read");
        assertEquals(1, result.size());
        assertEquals(EnumSet.of(EconomyScope.BANK_READ), result.get("mycasino"),
            "the later line is the operator's more recent intent");
    }

    @Test
    void allowlistSurvivesOneBadLineAmongGoodOnes() throws Exception {
        Map<String, Set<EconomyScope>> result = parseAllowedMods(
            "mycasino=wallet_write", "garbage", "shopmod=bank_read");
        assertEquals(2, result.size(),
            "a typo in one line must not cost an operator every other integration");
        assertTrue(result.containsKey("mycasino"));
        assertTrue(result.containsKey("shopmod"));
    }

    @Test
    void allowlistEmptyInputDeniesEverything() throws Exception {
        assertTrue(parseAllowedMods().isEmpty(), "empty config is the deny-by-default case");
        assertTrue(parseAllowedMods((String[]) null).isEmpty());
    }

    @Test
    void allowlistScopeSetsAreUnmodifiable() throws Exception {
        Set<EconomyScope> scopes = parseAllowedMods("mycasino=wallet_read").get("mycasino");
        assertThrows(UnsupportedOperationException.class,
            () -> scopes.add(EconomyScope.BANK_WRITE),
            "a granted scope set must not be widenable by whoever holds a reference to it");
    }
}
