package com.ridebooking.driverservice.provider;

import in.zeta.oms.sandbox.model.object.ObjectProvider;
import in.zeta.oms.sandbox.model.realm.Realm;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import olympus.common.JID;
import org.springframework.stereotype.Component;

@Component
public class UserProvider implements ObjectProvider<String> {

    public static final String OBJECT_TYPE = "rider";

    @Override
    public CompletionStage<Optional<String>> getObject(JID jid, Realm realm, Long tenantID) {
        return CompletableFuture.completedFuture(Optional.of(OBJECT_TYPE));
    }
}