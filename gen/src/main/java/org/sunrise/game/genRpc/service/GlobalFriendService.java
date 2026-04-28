package org.sunrise.game.genRpc.service;

public interface GlobalFriendService {
    void sendFriendRequest();
    void handleFriendRequest();
    void deleteFriend();
    void getFriends();
    void getFriendRequests();
}
