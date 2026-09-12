package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectedTriangleOrderTest {
    private int relation(ProjectedTriangleOrder.Triangle a, ProjectedTriangleOrder.Triangle b) {
        return ProjectedTriangleOrder.relation(a,b,new double[20],new double[20]);
    }

    @Test void overlappingTipsOverrideMisleadingTriangleCentres() {
        var a=ProjectedTriangleOrder.Triangle.of(0,0,.7, 1,0,.7, 0,1,.7);
        var b=ProjectedTriangleOrder.Triangle.of(.1,.1,.6, .2,.1,.6, 10,10,.99);
        assertEquals(1,relation(a,b));
        assertEquals(-1,relation(b,a));
        var result=ProjectedTriangleOrder.refine(new ProjectedTriangleOrder.Triangle[]{a,b},new int[]{1,0});
        assertArrayEquals(new int[]{0,1},result.order());
        assertEquals(0,result.cycleBreaks());
    }

    @Test void windingDoesNotChangeFrontBackRelationship() {
        var a=ProjectedTriangleOrder.Triangle.of(0,0,.8, 1,0,.8, 0,1,.8);
        var b=ProjectedTriangleOrder.Triangle.of(0,0,.3, 0,1,.3, 1,0,.3);
        assertEquals(1,relation(a,b));
    }

    @Test void sharedEdgesAndCoplanarFacesDoNotCreateCycles() {
        var a=ProjectedTriangleOrder.Triangle.of(0,0,.8, 1,0,.8, 0,1,.8);
        var b=ProjectedTriangleOrder.Triangle.of(1,0,.3, 1,1,.3, 0,1,.3);
        assertEquals(0,relation(a,b));
        assertEquals(0,relation(a,a));
    }

    @Test void genuineCrossingsAreReportedRatherThanPretendingAValidWholeTriangleOrder() {
        var a=ProjectedTriangleOrder.Triangle.of(0,0,.2, 1,0,.8, 0,1,.2);
        var b=ProjectedTriangleOrder.Triangle.of(0,0,.5, 1,0,.5, 0,1,.5);
        assertEquals(2,relation(a,b));
        var result=ProjectedTriangleOrder.refine(new ProjectedTriangleOrder.Triangle[]{a,b},new int[]{1,0});
        assertEquals(1,result.crossingPairs());
        assertArrayEquals(new int[]{1,0},result.order());
    }

    @Test void degenerateAndOffscreenDisjointTrianglesAreSafe() {
        assertNull(ProjectedTriangleOrder.Triangle.of(0,0,0, 1,1,1, 2,2,2));
        var a=ProjectedTriangleOrder.Triangle.of(0,0,.5, 1,0,.5, 0,1,.5);
        var b=ProjectedTriangleOrder.Triangle.of(3,3,.8, 4,3,.8, 3,4,.8);
        assertEquals(0,relation(a,b));
        assertArrayEquals(new int[]{1,0},ProjectedTriangleOrder.refine(new ProjectedTriangleOrder.Triangle[]{a,b},new int[]{1,0}).order());
    }
}
