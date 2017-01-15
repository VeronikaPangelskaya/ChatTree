package ru.chatpacket;

import ru.Node;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.*;

import static ru.chatpacket.ProtocolMagicValues.TEXT;

// Сообщение, типа текстовое сообщение. В нем, как и во всех других типах сообщения, реализуется специальная структура
// пакета, а именно Header (Тип сообщения, Sequence Number) и далее уже остальная информация (в данном случае, длина
// текста + сам текст)
public class ChatTextMessage implements ChatPacket {
    private String userText = "";
    private ArrayList<DeliveryDataTuple> recipientsList = new ArrayList<>();
    private long lastSendingTime;
    private int sendCounter = 0;
    private Random randGenerator = new Random();

    //записли текст сообщения
    public ChatTextMessage(String _userText) {
        userText = _userText;
    }

    @Override
    public void setRecipient(Node recipient) {
        DeliveryDataTuple currentPacket = new DeliveryDataTuple();
        currentPacket.recipient = recipient;
        currentPacket.sequenceNumber = randGenerator.nextInt(Integer.MAX_VALUE);
        recipientsList.add(currentPacket);
    }

    public void setRecipient(Set<Node> _recipients) {
        for (Node currentNode : _recipients) {
            setRecipient(currentNode);
        }
    }

    @Override
    public void send(DatagramSocket socket) {
        ByteBuffer parser = ByteBuffer.wrap(new byte[userText.getBytes().length + 9]);

        for (DeliveryDataTuple currentRecipient : recipientsList) {
            parser.clear();
            parser.put(TEXT);
            parser.putInt(currentRecipient.sequenceNumber);
            parser.putInt(userText.getBytes().length);
            parser.put(userText.getBytes());
            DatagramPacket newPacket = new DatagramPacket(parser.array(), parser.array().length);
            newPacket.setAddress(currentRecipient.recipient.getAddress());
            newPacket.setPort(currentRecipient.recipient.getPort());

            try {
                socket.send(newPacket);

            } catch (IOException e) {
                System.out.println("I/O Error! Can't sending to client " + currentRecipient);
            }
        }

        lastSendingTime = System.currentTimeMillis();
        sendCounter++;
    }

    @Override
    public void markAsReceiving(int _sequenceNumber) {
        Iterator<DeliveryDataTuple> iter = recipientsList.iterator();

        while (iter.hasNext()) {
            DeliveryDataTuple recipient = iter.next();
            if (recipient.sequenceNumber == _sequenceNumber) {
                iter.remove();
                break;
            }
        }
    }

    @Override
    public boolean isDeliver() {
        return recipientsList.isEmpty();
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
