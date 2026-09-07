package com.ridebooking.rideservice.provider;

import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAccessControlProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SandboxProviderTest {

    @Mock
    private SandboxAccessControlProvider sacp;

    @Test
    void driverProvider_getObject_returnsDriverType() {
        DriverProvider provider = new DriverProvider();
        CompletionStage<Optional<String>> result = provider.getObject(null, null, 0L);
        Optional<String> value = result.toCompletableFuture().join();
        assertTrue(value.isPresent());
        assertEquals(DriverProvider.OBJECT_TYPE, value.get());
    }

    @Test
    void userProvider_getObject_returnsRiderType() {
        UserProvider provider = new UserProvider();
        CompletionStage<Optional<String>> result = provider.getObject(null, null, 0L);
        Optional<String> value = result.toCompletableFuture().join();
        assertTrue(value.isPresent());
        assertEquals(UserProvider.OBJECT_TYPE, value.get());
    }

    @Test
    void rideProvider_getObject_returnsRideType() {
        RideProvider provider = new RideProvider();
        CompletionStage<Optional<String>> result = provider.getObject(null, null, 0L);
        Optional<String> value = result.toCompletableFuture().join();
        assertTrue(value.isPresent());
        assertEquals(RideProvider.OBJECT_TYPE, value.get());
    }

    @Test
    void sandboxConfig_registersAllObjectProviders() {
        SandboxConfig config = new SandboxConfig();
        UserProvider userProvider = new UserProvider();
        DriverProvider driverProvider = new DriverProvider();
        RideProvider rideProvider = new RideProvider();

        SandboxAccessControlProvider result = config.getSandboxAccessControlProvider(
                userProvider, driverProvider, rideProvider, sacp);

        verify(sacp).registerObjectProvider(UserProvider.OBJECT_TYPE, userProvider);
        verify(sacp).registerObjectProvider(DriverProvider.OBJECT_TYPE, driverProvider);
        verify(sacp).registerObjectProvider(RideProvider.OBJECT_TYPE, rideProvider);
        assertSame(sacp, result);
    }
}
