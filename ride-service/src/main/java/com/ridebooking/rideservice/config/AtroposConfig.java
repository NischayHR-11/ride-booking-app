package com.ridebooking.rideservice.config;

import com.google.gson.Gson;
import in.zeta.oms.atropos.client.AtroposPublisherClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AtroposConfig {

    @Bean
    public AtroposPublisherClient atroposPublisherClient() {
        return new AtroposPublisherClient();
    }

    @Bean
    public Gson gson() {
        return new Gson();
    }
}
