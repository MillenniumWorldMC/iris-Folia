package art.arcane.iris.core.tools;

import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.spi.IrisServices;
import org.bukkit.World;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IrisToolbeltPackReferenceTest {
    @After
    public void cleanServices() {
        IrisServices.remove(StudioSVC.class);
    }

    @Test
    public void plainPackUsesMatchingDimensionKey() {
        IrisToolbelt.PackReference reference = IrisToolbelt.parsePackReference("overworld");

        assertEquals("overworld", reference.pack());
        assertEquals("overworld", reference.dimension());
        assertFalse(reference.explicitDimension());
    }

    @Test
    public void explicitDimensionKeepsPackAndDimensionSeparate() {
        IrisToolbelt.PackReference reference = IrisToolbelt.parsePackReference(" custom_pack : dimensions/sky ");

        assertEquals("custom_pack", reference.pack());
        assertEquals("dimensions/sky", reference.dimension());
        assertTrue(reference.explicitDimension());
    }

    @Test
    public void malformedReferencesAreRejected() {
        assertNull(IrisToolbelt.parsePackReference(null));
        assertNull(IrisToolbelt.parsePackReference(""));
        assertNull(IrisToolbelt.parsePackReference(":"));
        assertNull(IrisToolbelt.parsePackReference("pack:"));
        assertNull(IrisToolbelt.parsePackReference(":dimension"));
    }

    @Test
    public void accessReturnsNullAfterStudioServiceIsRemoved() {
        IrisServices.remove(StudioSVC.class);
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> null
        );

        assertNull(IrisToolbelt.access(world));
    }
}
