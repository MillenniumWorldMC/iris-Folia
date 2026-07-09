package art.arcane.iris.core.splash;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IrisSplashComposerTest {
    @Test
    public void composeInfoUsesCurrentVersionWithoutStaleReleaseTag() {
        String[] info = IrisSplashComposer.composeInfo("4.0.0-26.2", "Paper 26.2", IrisSplashComposer.InfoStyle.PLAIN);

        assertEquals(" Iris, Dimension Engine [4.0]", info[1]);
        assertEquals(" Version: 4.0.0-26.2", info[2]);
        assertFalse(String.join("\n", info).contains("RC.1.1.6"));
    }
}
