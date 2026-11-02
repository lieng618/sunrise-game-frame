package org.sunrise.game.global.service.mail;

import org.junit.jupiter.api.Test;
import org.sunrise.game.core.message.RpcDataSanitizer;
import org.sunrise.game.rpc.function.RpcResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalMailServiceTest {

    @Test
    void acceptsSetNormalizedToArrayListByRpcSanitizer() {
        Set<String> returnedIds = new LinkedHashSet<>(List.of("player-1", "player-2"));
        RpcResult result = new RpcResult();
        result.setData(new Object[]{"humanIds", RpcDataSanitizer.sanitize(returnedIds)});

        assertEquals(List.of("player-1", "player-2"), GlobalMailService.extractHumanIds(result));
    }
}
