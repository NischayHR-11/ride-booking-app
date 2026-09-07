package com.ridebooking.rideservice.provider;

import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAccessControlProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SandboxConfig {

    @Bean
    @Primary
    public SandboxAccessControlProvider getSandboxAccessControlProvider(
                                                                        UserProvider userProvider,
                                                                        DriverProvider driverProvider,
                                                                        RideProvider rideProvider,
                                                                        SandboxAccessControlProvider sacp) {
        sacp.registerObjectProvider(UserProvider.OBJECT_TYPE, userProvider);
        sacp.registerObjectProvider(DriverProvider.OBJECT_TYPE, driverProvider);
        sacp.registerObjectProvider(RideProvider.OBJECT_TYPE, rideProvider);
        return sacp;
    }
}
