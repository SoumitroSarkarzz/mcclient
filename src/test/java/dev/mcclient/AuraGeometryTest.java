package dev.mcclient;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuraGeometryTest {
    @Test void hitboxSurfaceRatherThanEntityFeetControlsDistance() {
        Box target = new Box(2, 0, -0.3, 2.6, 1.8, 0.3);
        Vec3d eyes = new Vec3d(0, 1.62, 0);
        Vec3d point = AuraController.aimPoint(target, eyes);
        assertEquals(1.62, point.y, 0.00001);
        assertEquals(2.001, point.x, 0.00001);
        assertTrue(eyes.squaredDistanceTo(point) < 2.01 * 2.01);
    }
    @Test void targetsBehindPlayerAreNotExcludedByGeometry() {
        Vec3d eyes = new Vec3d(0, 1.62, 0);
        Vec3d point = AuraController.aimPoint(new Box(-0.3, 0, -2.6, 0.3, 1.8, -2), eyes);
        assertTrue(point.z < 0);
        assertEquals(2.001, eyes.distanceTo(point), 0.00001);
    }
    @Test void overlappingAndTallHitboxesStayWithinTheirBounds() {
        Box target = new Box(-0.5, 0, -0.5, 0.5, 3, 0.5);
        Vec3d point = AuraController.aimPoint(target, new Vec3d(0, 1.62, 0));
        assertTrue(target.contains(point));
        assertEquals(1.62, point.y, 0.00001);
    }
    @Test void beyondReachTargetRemainsOutsideReach() {
        Vec3d eyes = new Vec3d(0, 1.62, 0);
        Vec3d point = AuraController.aimPoint(new Box(3.1, 0, 0, 3.7, 1.8, 0.6), eyes);
        assertTrue(eyes.squaredDistanceTo(point) > 3 * 3);
    }
}
