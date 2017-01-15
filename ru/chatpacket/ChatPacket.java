package ru.chatpacket;

import ru.Node;

import java.net.DatagramSocket;

// Интерфейс сообщения в сети
public interface ChatPacket {
    void send(DatagramSocket socket);
    void setRecipient(Node recipient);

    void markAsReceiving(int sequenceNumber);
    boolean isDeliver();
    Long getLastSendingTime();
    int getSendingCount();
}
