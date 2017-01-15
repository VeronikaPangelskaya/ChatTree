package ru;

import java.net.InetAddress;

public class Node
{
    //имя узла, ipAddress - его ip-шник, port - номер его порта
    private String nodeName = "";
    //ip-адрес узла
    private InetAddress ipAddress;
    //порт узла
    private int port;

    //макисмальное количество сообщений
    private final int MAXIDCOUNT = 512;
    //список всех полученных сообщений
    private int[] idList = new int[MAXIDCOUNT];
    //текущая позиция в списке
    private int currentPos = 0;

    //создание узла с именем
    public Node(String _nodeName, InetAddress _ipAddress, int _port) {
        nodeName = _nodeName;
        ipAddress = _ipAddress;
        port = _port;
    }

    //создание узла без имени
    public Node(InetAddress _ipAddress, int _port) {
        ipAddress = _ipAddress;
        port = _port;
    }

    //присвоение имени узлу
    public void setNodeName(String _nodeName) {
        nodeName = _nodeName;
    }

    //привоение ip-шника
    public void setIPAddress(InetAddress _ipAddress) {
        ipAddress = _ipAddress;
    }

    //получение имени
    public String getNodeName() {
        return nodeName;
    }

    //получение ip-шника
    public InetAddress getAddress() {
        return ipAddress;
    }

    //получение порта
    public int getPort() {
        return port;
    }

    //добавить в список ID полученного сообщения
    public void addMessageID(int id) {
        int pos = currentPos % MAXIDCOUNT;
        idList[pos] = id;
        currentPos++;
    }

    //проверка на существование ID в списке
    //вернет true, если ID есть в списке, иначе false
    public boolean isExist(int id) {
        for (int i = 0; i < idList.length; i++) {
            if (idList[i] == id)
                return true;
        }

        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Node))
            return false;

        if (obj == this)
            return true;

        Node secondNode = (Node) obj;

        if (this.getAddress().equals(secondNode.getAddress()) && this.getPort() == secondNode.getPort()) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return getAddress().hashCode() + getPort();
    }
}
