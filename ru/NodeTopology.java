package ru;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

// Топология текущего узла (сведения о его родителе и детях)
public class NodeTopology
{
    //родителький узел
    private Node parent = null;
    //коллекция узлов-потомков
    private Set<Node> childrens = new HashSet<>();

    public NodeTopology() {}

    //добавление потомка в топологию
    void addChildrenNode(Node child) {
        childrens.add(child);
    }

    //получение узла родителя
    Node getParent() {
        return parent;
    }

    //добавление узла родителя
    void setParent(Node _parent) {
        parent = _parent;
    }

    //получение коллекции потомков
    public Set<Node> getChildrens() {
        return childrens;
    }

    //получение коллекции потомков + родителя
    public Set<Node> getTopology() {
        Set<Node> topology = new HashSet<>();
        topology.addAll(childrens);

        if (parent != null)
            topology.add(parent);

        return topology;
    }

    //получение имени узла по его адресу
    public String getNameByAddress(InetAddress _address, int _port) {
        //прошлись до детям, если совпадает ip-шник и порт, то возвращаем имя узла
        for (Node currentNode : childrens) {
            if (currentNode.getAddress().equals(_address) && currentNode.getPort() == _port)
                return currentNode.getNodeName();
        }

        //если это родитель
        if (parent != null && parent.getAddress().equals(_address) && parent.getPort() == _port)
            return parent.getNodeName();

        return null;
    }
}
