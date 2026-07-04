package com.navneetsn18.smsforwarder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;

public class ConfigMessageTest {

    @Test public void nonConfigMessagesReturnNull() {
        assertNull(ConfigMessage.parse("Your OTP is 482913"));
        assertNull(ConfigMessage.parse(null));
        assertNull(ConfigMessage.parse(""));
    }

    @Test public void addIsDefaultVerbAndParsesAllFields() {
        ConfigMessage.Parsed p = ConfigMessage.parse(
            "swoosh pin:1234;name:Dad OTP;to:9988776655,8877665544;from:HDFC;contains:OTP;days:7;strip:Dear Customer");
        assertEquals("add", p.verb);
        assertEquals("1234", p.fields.get("pin"));

        Rule r = new Rule();
        ConfigMessage.merge(r, p.fields, 1_000_000L);
        assertEquals("Dad OTP", r.name);
        assertEquals(Arrays.asList("9988776655", "8877665544"), r.destinations);
        assertEquals("HDFC", r.senderContains);
        assertEquals("OTP", r.bodyContains);
        assertEquals(Arrays.asList("Dear Customer"), r.stripWords);
        assertEquals(1_000_000L + 7L * 24 * 60 * 60 * 1000, r.expiresAt);
    }

    @Test public void verbsParse() {
        assertEquals("list", ConfigMessage.parse("swoosh list pin:1").verb);
        assertEquals("add", ConfigMessage.parse("Swoosh:add pin:1;name:x;to:9988776655").verb);

        ConfigMessage.Parsed mod = ConfigMessage.parse("swoosh mod:2 pin:1;to:9911223344");
        assertEquals("mod", mod.verb);
        assertEquals(2, mod.index);
        assertEquals("9911223344", mod.fields.get("to"));

        ConfigMessage.Parsed del = ConfigMessage.parse("swoosh del:12 pin:1");
        assertEquals("del", del.verb);
        assertEquals(12, del.index);
        assertEquals("1", del.fields.get("pin"));
    }

    @Test public void mergeIsPartial() {
        Rule r = new Rule();
        r.name = "keep me";
        r.destinations = Arrays.asList("9988776655");
        r.bodyContains = "OTP";
        ConfigMessage.merge(r, ConfigMessage.parse("swoosh mod:1 pin:1;contains:CODE").fields, 0);
        assertEquals("keep me", r.name);
        assertEquals(Arrays.asList("9988776655"), r.destinations);
        assertEquals("CODE", r.bodyContains);
    }

    @Test public void badValuesThrow() {
        try {
            ConfigMessage.merge(new Rule(), ConfigMessage.parse("swoosh pin:1;name:x;to:123").fields, 0);
            fail("expected invalid to");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("to"));
        }
        try {
            ConfigMessage.merge(new Rule(), ConfigMessage.parse("swoosh pin:1;days:soon").fields, 0);
            fail("expected bad days");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("days"));
        }
    }

    @Test public void optionalFieldsDefaultSanely() {
        Rule r = new Rule();
        ConfigMessage.merge(r, ConfigMessage.parse("swoosh pin:1;name:x;to:+91 99887 76655").fields, 0);
        assertEquals("", r.senderContains);
        assertEquals("", r.bodyContains);
        assertEquals(0, r.expiresAt);
        assertTrue(r.stripWords.isEmpty());
        assertTrue(r.enabled);
        assertEquals(Arrays.asList("+91 99887 76655"), r.destinations);
    }

    @Test public void daysZeroClearsExpiry() {
        Rule r = new Rule();
        r.expiresAt = 42;
        ConfigMessage.merge(r, ConfigMessage.parse("swoosh mod:1 pin:1;days:0").fields, 0);
        assertEquals(0, r.expiresAt);
    }

    @Test public void verbNotConfusedWithFields() {
        // "pin" first — no verb token, still add.
        assertNotNull(ConfigMessage.parse("swoosh pin:1;name:x;to:9988776655"));
        assertEquals("add", ConfigMessage.parse("swoosh pin:1;name:x;to:9988776655").verb);
    }
}
