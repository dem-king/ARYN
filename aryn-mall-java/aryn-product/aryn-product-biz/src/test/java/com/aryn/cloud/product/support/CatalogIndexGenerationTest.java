package com.aryn.cloud.product.support;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CatalogIndexGenerationTest {
    @Test void fullCatalogWalkDoesNotStopAtTwoThousand() {
        List<Integer> seen=new ArrayList<>();
        CatalogIndexRules.walkPages((cursor,size)->java.util.stream.IntStream.range(cursor==null?0:Integer.parseInt(cursor)+1,Math.min(2001,(cursor==null?0:Integer.parseInt(cursor)+1)+size)).mapToObj(String::valueOf).toList(),
            page->{for(String id:page) seen.add(Integer.parseInt(id));},500);
        assertEquals(2001,seen.size());assertEquals(2000,seen.get(2000));
    }
    @Test void equalDimensionsDoNotMakeDifferentModelsCompatible() {
        assertFalse(CatalogIndexRules.compatible("model-a",2,"model-b",2));
        assertFalse(CatalogIndexRules.compatible("model-a",2,"model-a",3));
        assertTrue(CatalogIndexRules.compatible("model-a",2,"model-a",2));
        assertThrows(IllegalArgumentException.class,()->CatalogVectorRecallService.dot(new float[]{1},new float[]{1,2}));
    }
    @Test void duplicatedOrUnsortedCursorFailsInsteadOfHanging() {
        assertThrows(IllegalStateException.class,()->CatalogIndexRules.walkPages((cursor,size)->List.of("a","a"),page->{},2));
    }
}
