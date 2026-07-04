package com.navneetsn18.smsforwarder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

public class ConfigMessageTest {

    @Test public void nonConfigMessagesReturnNull() {
        assertNull(ConfigMessage.parseFields("Your OTP is 482913"));
        assertNull(ConfigMessage.parseFields(null));
        assertNull(ConfigMessage.parseFields(""));
    }

    @Test public void parsesFullMessage() {
        Map<String, String> f = ConfigMessage.parseFields(
            "swoosh pin:1234;name:Dad OTP;to:9988776655,8877665544;from:HDFC;contains:OTP;days:7;strip:Dear Customer");
        assertNotNull(f);
        assertEquals("1234", f.get("pin"));

        Rule r = ConfigMessage.toRule(f, 1_000_000L);
        assertEquals("Dad OTP", r.name);
        assertEquals(Arrays.asList("9988776655", "8877665544"), r.destinations);
        assertEquals("HDFC", r.senderContains);
        assertEquals("OTP", r.bodyContains);
        assertEquals(Arrays.asList("Dear Customer"), r.stripWords);
        assertEquals(1_000_000L + 7L * 24 * 60 * 60 * 1000, r.expiresAt);
    }

    @Test public void colonPrefixAndCaseInsensitiveMarkerWork() {
        assertNotNull(ConfigMessage.parseFields("Swoosh:pin:1;name:x;to:9988776655"));
    }

    @Test public void missingRequiredFieldsThrow() {
        try {
            ConfigMessage.toRule(ConfigMessage.parseFields("swoosh pin:1;to:9988776655"), 0);
            fail("expected missing name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("name"));
        }
        try {
            ConfigMessage.toRule(ConfigMessage.parseFields("swoosh pin:1;name:x;to:123"), 0);
            fail("expected invalid to");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("to"));
        }
    }

    @Test public void optionalFieldsDefaultSanely() {
        Rule r = ConfigMessage.toRule(ConfigMessage.parseFields("swoosh pin:1;name:x;to:+91 99887 76655"), 0);
        assertEquals("", r.senderContains);
        assertEquals("", r.bodyContains);
        assertEquals(0, r.expiresAt);
        assertTrue(r.stripWords.isEmpty());
        assertTrue(r.enabled);
        assertEquals(Arrays.asList("+91 99887 76655"), r.destinations);
    }
}
