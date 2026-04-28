package org.sunrise.game.genRpc.service;

public interface GlobalMailService {
    void sendMail();
    void sendMailToMultiple();
    void sendMailToAll();
    void getPlayerMails();
    void readMail();
    void receiveMailAttachment();
    void deleteMail();
}
