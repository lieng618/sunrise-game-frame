package org.sunrise.game.genRpc.gen;

/**
 * RPC 调用 ID，由 {@link org.sunrise.game.genRpc.GenRpcStartUp} 自动生成，不要手动修改。
 */
public class CallEnum {
    /** {@code org.sunrise.game.external.service.ExternalRecvGameMessageService#recvMessage(long,byte[],String)} */
    public static final int ExternalRecvGameMessageService_recvMessage = 1;
    /** {@code org.sunrise.game.game.service.FriendRpcListenService#onFriendAdded(String,String)} */
    public static final int FriendRpcListenService_onFriendAdded = 2;
    /** {@code org.sunrise.game.game.service.FriendRpcListenService#onFriendDeleted(String,String)} */
    public static final int FriendRpcListenService_onFriendDeleted = 3;
    /** {@code org.sunrise.game.game.service.FriendRpcListenService#onNewFriendRequest(String)} */
    public static final int FriendRpcListenService_onNewFriendRequest = 4;
    /** {@code org.sunrise.game.game.service.GameRecvExternalMessageService#recvMessage(long,byte[],String)} */
    public static final int GameRecvExternalMessageService_recvMessage = 5;
    /** {@code org.sunrise.game.game.service.GameRecvGmBackMessageService#recvMessage(String,String)} */
    public static final int GameRecvGmBackMessageService_recvMessage = 6;
    /** {@code org.sunrise.game.game.service.GameRpcListenService#sendToAllHuman(int,int,byte[])} */
    public static final int GameRpcListenService_sendToAllHuman = 7;
    /** {@code org.sunrise.game.game.service.GameRpcListenService#sendToHuman(String,int,int,byte[])} */
    public static final int GameRpcListenService_sendToHuman = 8;
    /** {@code org.sunrise.game.global.service.chat.GlobalChatService#chat(String,String)} */
    public static final int GlobalChatService_chat = 9;
    /** {@code org.sunrise.game.global.service.chat.GlobalChatService#history(String)} */
    public static final int GlobalChatService_history = 10;
    /** {@code org.sunrise.game.global.service.friend.GlobalFriendService#deleteFriend(String,String)} */
    public static final int GlobalFriendService_deleteFriend = 11;
    /** {@code org.sunrise.game.global.service.friend.GlobalFriendService#getFriendRequests(String)} */
    public static final int GlobalFriendService_getFriendRequests = 12;
    /** {@code org.sunrise.game.global.service.friend.GlobalFriendService#getFriends(String)} */
    public static final int GlobalFriendService_getFriends = 13;
    /** {@code org.sunrise.game.global.service.friend.GlobalFriendService#handleFriendRequest(String,String,int)} */
    public static final int GlobalFriendService_handleFriendRequest = 14;
    /** {@code org.sunrise.game.global.service.friend.GlobalFriendService#sendFriendRequest(String,String)} */
    public static final int GlobalFriendService_sendFriendRequest = 15;
    /** {@code org.sunrise.game.global.service.mail.GlobalMailService#deleteMail(String,long)} */
    public static final int GlobalMailService_deleteMail = 16;
    /** {@code org.sunrise.game.global.service.mail.GlobalMailService#getPlayerMails(String)} */
    public static final int GlobalMailService_getPlayerMails = 17;
    /** {@code org.sunrise.game.global.service.mail.GlobalMailService#readMail(String,long)} */
    public static final int GlobalMailService_readMail = 18;
    /** {@code org.sunrise.game.global.service.mail.GlobalMailService#receiveMailAttachment(String,long)} */
    public static final int GlobalMailService_receiveMailAttachment = 19;
    /** {@code org.sunrise.game.global.service.mail.GlobalMailService#sendMail(String,int,String,String)} */
    public static final int GlobalMailService_sendMail = 20;
    /** {@code org.sunrise.game.global.service.mail.GlobalMailService#sendMailToAll(int,String,String)} */
    public static final int GlobalMailService_sendMailToAll = 21;
    /** {@code org.sunrise.game.global.service.mail.GlobalMailService#sendMailToMultiple(List<String>,int,String,String)} */
    public static final int GlobalMailService_sendMailToMultiple = 22;
    /** {@code org.sunrise.game.global.service.playerinfo.GlobalPlayerInfoService#getAllPlayerIds} */
    public static final int GlobalPlayerInfoService_getAllPlayerIds = 23;
    /** {@code org.sunrise.game.global.service.playerinfo.GlobalPlayerInfoService#getPlayerInfo(String)} */
    public static final int GlobalPlayerInfoService_getPlayerInfo = 24;
    /** {@code org.sunrise.game.global.service.playerinfo.GlobalPlayerInfoService#getPlayerInfos(List<String>)} */
    public static final int GlobalPlayerInfoService_getPlayerInfos = 25;
    /** {@code org.sunrise.game.global.service.playerinfo.GlobalPlayerInfoService#updatePlayerInfo(String,String,int,String,int,int)} */
    public static final int GlobalPlayerInfoService_updatePlayerInfo = 26;
    /** {@code org.sunrise.game.global.service.rank.GlobalRankService#getMyRank(int,String)} */
    public static final int GlobalRankService_getMyRank = 32;
    /** {@code org.sunrise.game.global.service.rank.GlobalRankService#getRankList(int,int,int)} */
    public static final int GlobalRankService_getRankList = 33;
    /** {@code org.sunrise.game.global.service.rank.GlobalRankService#removeFromRank(int,String)} */
    public static final int GlobalRankService_removeFromRank = 34;
    /** {@code org.sunrise.game.global.service.rank.GlobalRankService#updateRank(int,String,long)} */
    public static final int GlobalRankService_updateRank = 35;
    /** {@code org.sunrise.game.gmback.service.GmBackRecvMessageService#recvMessage(String,String)} */
    public static final int GmBackRecvMessageService_recvMessage = 27;
    /** {@code org.sunrise.game.http.service.HttpRecvMessageService#setAnnouncements(String)} */
    public static final int HttpRecvMessageService_setAnnouncements = 28;
    /** {@code org.sunrise.game.http.service.HttpRecvMessageService#setExternalServerStatus(boolean)} */
    public static final int HttpRecvMessageService_setExternalServerStatus = 29;
    /** {@code org.sunrise.game.http.service.HttpRecvMessageService#setWhitelist(String)} */
    public static final int HttpRecvMessageService_setWhitelist = 30;
    /** {@code org.sunrise.game.http.service.HttpRecvMessageService#updateExternalRemoteData(int,String,int,boolean,boolean,boolean)} */
    public static final int HttpRecvMessageService_updateExternalRemoteData = 31;
}
