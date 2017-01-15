package ru.chatpacket;

import ru.Node;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import static ru.chatpacket.ProtocolMagicValues.REPORT;

// Сообщение, типа отчета о доставке. В нем генерируем случайный идентификатор (Sequence Number)
public class ChatReportMessage implements ChatPacket {
    private InetAddress recipient;
    private int port;
    private int sequenceNumber;
    private boolean isDeliver = false;
    private long lastSendingTime;
    private int sendCounter = 0;

    public ChatReportMessage(int _sequenceNumber) {
        sequenceNumber = _sequenceNumber;
    }

    public void setRecipient(Node _recipient) {
        recipient = _recipient.getAddress();
        port = _recipient.getPort();
    }

    @Override
    public void send(DatagramSocket socket) {
        ByteBuffer parser = ByteBuffer.wrap(new byte[5]);
        parser.put(REPORT);
        parser.putInt(sequenceNumber);

        DatagramPacket packet = new DatagramPacket(parser.array(), parser.array().length);
        packet.setAddress(recipient);
        packet.setPort(port);

        try {
            socket.send(packet);
            lastSendingTime = System.currentTimeMillis();
        } catch (IOException e) {
            e.printStackTrace();
        }

        sendCounter++;
    }

    @Override
    public void markAsReceiving(int _sequenceNumber) {
        if (sequenceNumber == _sequenceNumber)
            isDeliver = true;
    }

    @Override
    public boolean isDeliver() {
        return isDeliver;
    }

    @Override
    public Long getLastSendingTime() {
        return lastSendingTime;
    }

    @Override
    public int getSendingCount() {
        return sendCounter;
    }
}
