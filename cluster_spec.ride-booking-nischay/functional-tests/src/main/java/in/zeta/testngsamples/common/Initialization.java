package in.zeta.testngsamples.common;

import in.zeta.cce.testngcommons.initializers.InitializerStrategy;
import org.testng.annotations.BeforeSuite;

import java.io.IOException;

import static in.zeta.cce.testngcommons.initializers.InitializerType.PROPERTIES;

public class Initialization {
    @BeforeSuite
    public void initialize() throws IOException {
        InitializerStrategy.get(PROPERTIES).initialize();
    }
}
