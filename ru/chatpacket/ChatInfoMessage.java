package ru.chatpacket;

import ru.Node;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import static ru.chatpacket.ProtocolMagicValues.*;

public class ChatInfoMessage implements ChatPacket {
    public class ChatInfoMessageException extends RuntimeException {
        public ChatInfoMessageException(String msg) {
            super(msg);
        }
    }

    private byte messageType;
    private Node newParent;
    private String nodeName;

    private long lastSendingTime;
    private int sendCounter = 0;
    private ArrayList<DeliveryDataTuple> recipientsList = new ArrayList<>();
    private Random randGenerator = new Random();

    //инициализация сообщения (какого оно типа)
    public ChatInfoMessage(byte _messageType) {
        messageType = _messageType;
    }

    //установка получателя
    public void setRecipient(Node recipient) {
        DeliveryDataTuple currentTuple = new DeliveryDataTuple();
        currentTuple.recipient = recipient;
        currentTuple.sequenceNumber = randGenerator.nextInt(Integer.MAX_VALUE);
        recipientsList.add(currentTuple);
    }

    //установка списка получателей
    public void setRecipient(Set<Node> recipients) {
        for (Node current : recipients) {
            setRecipient(current);
        }
    }

    //устанавили наше имя
    public void setNodeName(String _nodeName) throws ChatInfoMessageException {
        if (messageType == CHILD || messageType == PARENT)
            nodeName = _nodeName;
        else
            throw new ChatInfoMessageException("Illegal operation for non CHILD/PARENT message");
    }

    public void setNewParentNode(Node _newParent) throws ChatInfoMessageException {
        if (messageType == NEWPARENT)
            newParent = _newParent;
        else
            throw new ChatInfoMessageException("Illegal operation for non NEWPARENT message");
    }

    @Override
    public void send(DatagramSocket socket) {
        ByteBuffer parser;

        //если сообщение для соединения с родителем/потомком
        if (messageType == CHILD || messageType == PARENT)
            parser = ByteBuffer.wrap(new byte[nodeName.getBytes().length + 10]);
        //если сообщение о новом родителе
        else if (messageType == NEWPARENT)
            parser = ByteBuffer.wrap(new byte[14]);
        //если сообщение о отстутсвии родителя/потомков
        else
            parser = ByteBuffer.wrap(new byte[6]);

        for (DeliveryDataTuple recipient : recipientsList)
        {
            //тип сообщения - INFO
            parser.clear();
            parser.put(INFO);

            //номер сообщения
            parser.putInt(recipient.sequenceNumber);
            //тип INFO сообщения
            parser.put(messageType);

            //если мы хотим сообщить, что мы потомок/родитель
            if (messageType == CHILD || messageType == PARENT)
            {
                //длина + наше имя
                parser.putInt(nodeName.getBytes().length);
                parser.put(nodeName.getBytes());
            }
            //если сообщение о новом родителе
            else if (messageType == NEWPARENT)
            {
                //ip-адрес + порт
                parser.put(newParent.getAddress().getAddress());
                parser.putInt(newParent.getPort());
            }

            try {
                //создали пакет
                DatagramPacket packet = new DatagramPacket(parser.array(), parser.array().length);
                //кому отправить
                packet.setAddress(recipient.recipient.getAddress());
                packet.setPort(recipient.recipient.getPort());
                //отправили пакет
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //отметили время отправки последнего сообщения
        lastSendingTime = System.currentTimeMillis();
        //увеличили количество отправлений
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
