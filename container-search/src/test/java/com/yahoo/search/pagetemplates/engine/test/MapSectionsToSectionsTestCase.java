// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine.test;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.PageTemplate;
import com.yahoo.search.pagetemplates.engine.Organizer;
import com.yahoo.search.pagetemplates.engine.Resolution;
import com.yahoo.search.pagetemplates.engine.Resolver;
import com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.pagetemplates.model.MapChoice;
import com.yahoo.search.pagetemplates.model.PageElement;
import com.yahoo.search.pagetemplates.model.Section;
import com.yahoo.search.result.HitGroup;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class MapSectionsToSectionsTestCase extends ExecutionAbstractTestCase {

    @Test
    public void testExecution() {
        // Create the page template
        Choice page=Choice.createSingleton(importPage("MapSectionsToSections.xml"));

        // Create a federated result
        Query query=new Query();
        Result result=new Result(query);
        result.hits().add(createHits("source1",3));
        result.hits().add(createHits("source2",4));
        result.hits().add(createHits("source3",5));
        result.hits().add(createHits("source4",6));
        result.hits().add(createHits("source5",7));

        // Resolve
        Resolver resolver=new DeterministicResolverAssertingMethod();
        Resolution resolution=resolver.resolve(page,query,result);
        Map<String, List<PageElement>> mapping=
                resolution.getResolution((MapChoice)((PageTemplate)page.get(0).get(0)).getSection().elements().get(2));
        assertNotNull(mapping);
        assertEquals("box1",((Section)mapping.get("box1holder").get(0)).getId());
        assertEquals("box2",((Section)mapping.get("box2holder").get(0)).getId());
        assertEquals("box3",((Section)mapping.get("box3holder").get(0)).getId());
        assertEquals("box4",((Section)mapping.get("box4holder").get(0)).getId());

        // Execute
        Organizer organizer =new Organizer();
        organizer.organize(page,resolution,result);

        // Check execution:
        // Two subsections, each containing two sub-subsections with one source each
        assertEquals(2,result.hits().size());
        HitGroup row1=(HitGroup)result.hits().get(0);
        HitGroup column11=(HitGroup)row1.get(0);
        HitGroup column12=(HitGroup)row1.get(1);
        HitGroup row2=(HitGroup)result.hits().get(1);
        HitGroup column21a=(HitGroup)row2.get(0);
        HitGroup column21b=(HitGroup)row2.get(1);
        HitGroup column22=(HitGroup)row2.get(2);
        assertEqualHitGroups(createHits("source1",3),column11);
        assertEqualHitGroups(createHits("source2",4),column12);
        assertEqualHitGroups(createHits("source3",5),column21a);
        assertEqualHitGroups(createHits("source5",7),column21b);
        assertEqualHitGroups(createHits("source4",6),column22);

        // Check rendering
        assertRendered(result,"MapSectionsToSectionsResult.xml");
    }

    /** Same as deterministic resolver, but asserts that it received the correct method names for each map choice */
    private static class DeterministicResolverAssertingMethod extends DeterministicResolver {

        /** Chooses the last alternative of any choice */
        @Override
        public void resolve(MapChoice mapChoice, Query query, Result result, Resolution resolution) {
            assertEquals("myMethod",mapChoice.getMethod());
            super.resolve(mapChoice,query,result,resolution);
        }

    }

}
