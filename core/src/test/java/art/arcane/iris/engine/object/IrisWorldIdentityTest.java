package art.arcane.iris.engine.object;

import org.bukkit.NamespacedKey;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisWorldIdentityTest {
    @Test
    public void usesBukkitKeyBeforePlatformIdentity() {
        IrisWorld world = IrisWorld.builder()
                .key(new NamespacedKey("iris", "bukkit"))
                .platformIdentity("iris:modded")
                .build();

        assertEquals("iris:bukkit", world.identity());
    }

    @Test
    public void usesPlatformIdentityWithoutBukkitKey() {
        IrisWorld world = IrisWorld.builder()
                .platformIdentity("minecraft:the_nether")
                .build();

        assertEquals("minecraft:the_nether", world.identity());
    }
}
