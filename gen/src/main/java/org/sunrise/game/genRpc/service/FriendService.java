package org.sunrise.game.genRpc.service;

public interface FriendService {
    void sendFriendRequest();
    void handleFriendRequest();
    void deleteFriend();
    void getFriends();
    void getFriendRequests();
}
