// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.jdisc.http.HttpRequest.Method;

/**
 * API check for HttpRequest.Builder.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class RequestBuilderTestCase {
    HttpRequest.Builder b;

    @Before
    public void setUp() throws Exception {
        HttpRequest r = HttpRequest.createTestRequest("http://ssh:22/alpha?bravo=charlie", Method.GET);
        b = new HttpRequest.Builder(r);
    }

    @After
    public void tearDown() throws Exception {
        b = null;
    }

    @Test
    public final void testBasic() {
        HttpRequest r = b.put("delta", "echo").createDirectRequest();
        assertEquals("charlie", r.getProperty("bravo"));
        assertEquals("echo", r.getProperty("delta"));
    }

    @Test
    public void testRemove() {
        HttpRequest orig = b.put("delta", "echo").createDirectRequest();

        HttpRequest child = new HttpRequest.Builder(orig).removeProperty("delta").createDirectRequest();
        assertFalse(child.propertyMap().containsKey("delta"));
    }

}
