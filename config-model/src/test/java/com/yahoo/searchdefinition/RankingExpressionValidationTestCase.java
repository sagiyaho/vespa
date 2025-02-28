// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.derived.DerivedConfiguration;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
/**
 * @author bratseth
 */
public class RankingExpressionValidationTestCase extends SchemaTestCase {

    @Test
    public void testInvalidExpressionProducesException() throws ParseException {
        assertFailsExpression("&/%(/%&");
        assertFailsExpression("if(a==b,b)");
    }

    private void assertFailsExpression(String expression) throws ParseException {
        try {
            RankProfileRegistry registry = new RankProfileRegistry();
            Search search = importWithExpression(expression, registry);
            new DerivedConfiguration(search, registry); // cause rank profile parsing
            fail("No exception on incorrect ranking expression " + expression);
        } catch (IllegalArgumentException e) {
            // Success
            assertTrue(Exceptions.toMessageString(e).startsWith("Illegal first phase ranking function: Could not parse ranking expression '" + expression + "' in default, firstphase.:"));
        }
    }

    private Search importWithExpression(String expression, RankProfileRegistry registry) throws ParseException {
        SearchBuilder builder = new SearchBuilder(registry);
        builder.importString("search test {" +
                             "    document test { " +
                             "        field a type string { " +
                             "            indexing: index " +
                             "        }" +
                             "    }" +
                             "    rank-profile default {" +
                             "        first-phase {" +
                             "            expression: " + expression +
                             "        }" +
                             "    }" +
                             "}");
        builder.build();
        return builder.getSearch();
    }

}
