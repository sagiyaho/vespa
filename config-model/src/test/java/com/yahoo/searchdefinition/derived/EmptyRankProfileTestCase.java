// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.DataType;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SchemaTestCase;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import org.junit.Test;

/**
 * Tests deriving rank for files from search definitions
 *
 * @author bratseth
 */
public class EmptyRankProfileTestCase extends SchemaTestCase {

    @Test
    public void testDeriving() {
        Search search = new Search("test");
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(search);
        SDDocumentType doc = new SDDocumentType("test");
        search.addDocument(doc);
        doc.addField(new SDField("a", DataType.STRING));
        SDField field = new SDField("b", DataType.STRING);
        field.setLiteralBoost(500);
        doc.addField(field);
        doc.addField(new SDField("c", DataType.STRING));

        search = SearchBuilder.buildFromRawSearch(search, rankProfileRegistry, new QueryProfileRegistry());
        new DerivedConfiguration(search, rankProfileRegistry);
    }

}
