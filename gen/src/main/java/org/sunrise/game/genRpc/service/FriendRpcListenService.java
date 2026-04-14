package org.sunrise.game.genRpc.service;

public interface FriendRpcListenService  {
    void onNewFriendRequest();
    void onFriendAdded();
    void onFriendDeleted();
}
