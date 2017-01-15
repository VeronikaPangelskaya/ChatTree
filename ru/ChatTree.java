package ru;

import ru.chatpacket.ChatPacket;
import ru.chatpacket.ChatReportMessage;
import ru.chatpacket.ChatTextMessage;
import ru.chatpacket.ChatInfoMessage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import static ru.chatpacket.ProtocolMagicValues.*;

public class ChatTree {
    private final int timeout = 1500;
    final int maxUDPPacketLength = 1460;
    final int MAX_SEND_COUNT = 10;

    DatagramSocket socket = null;
    // Структура, которая хранит топологию текущего узла (его родителя и множество детей)
    NodeTopology currentNodeTopology = null;
    Thread inputThread;

    Node currentNode = null;
    int packageLostPercent = 15;

    /*
        Общий формат для первых 5 байт любого сообщения в протоколе:
        ==============================================
        =     1 байт     =          4 байта          =
        = (тип сообщения)=(Уникальный ID сообщения)  =
        =                       Sequence Number      =
        ==============================================
    */

    /* Типы сообщений, которые есть в протоколе
        REPORT - отчет о доставке
        INFO - служебное сообщение, которое содержит информацию о связях между узлами
        TEXT - текстовое сообщение для участников чата
    */

    // Очередь, которая содержит сообщения, которые были отправлены, но статус их доставки еще не подтвержден
    final ArrayList<ChatPacket> waitingReport = new ArrayList<>();

    void parseArguments(String[] args) {
        //если недостаточно аргументов
        if (args.length < 2)
            throw new RuntimeException("[ERROR] Need more arguments!");

        try {
            //создаем наш узел  с текущем именем и портом
            currentNode = new Node(args[0], null, Integer.parseInt(args[1]));

            //выставляем процент потерь
            if (args.length > 2)
                packageLostPercent = Integer.parseInt(args[2]);

            //если задан адрес и порт родителя, то создаем соостветсвующий узел и добавляем его в топологию
            if (args.length > 4) {
                InetAddress parentAddress = InetAddress.getByName(args[3]);
                int parentAddressPort = Integer.parseInt(args[4]);
                currentNodeTopology.setParent(new Node(parentAddress, parentAddressPort));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("[ERROR] Arguments parser error!");
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("[INFO] Incorrect parent IP address. This node is root");
        }
    }

    // Кладем сообщение в очередь
    private void addWaitingMessage(ChatPacket message) {
        synchronized (waitingReport) {
            waitingReport.add(message);
        }
    }

    // Находим в коллекции сообщения с идентификатором (sequenceNumber),
    // и удаляем его из коллекции (отмечаем, как доставленное)
    private void checkDeliveredMessages(int sequenceNumber) {
        synchronized (waitingReport) {
            Iterator<ChatPacket> iter = waitingReport.iterator();

            //пока есть не подтвержденные сообщения
            while (iter.hasNext()) {
                //получаем ссобщение
                ChatPacket currentPacket = iter.next();
                //удалили получателя из списка в сообщении
                currentPacket.markAsReceiving(sequenceNumber);

                //удалили из нашего списка
                if (currentPacket.isDeliver())
                    iter.remove();
            }

            //начали работать сов семи потоками
            waitingReport.notifyAll();
        }
    }

    //обработчик сообщений
    private void messageHandler(DatagramPacket packet) {
        byte[] message = packet.getData();
        //из массива байтов сделали буффер
        ByteBuffer parser = ByteBuffer.wrap(message);
        //получили номер сообщения
        int sequenceNumber = parser.getInt(SEQNUMPOS);

        //Проверяем текущее сообщение на признак того, что мы его уже обрабатывали
        //проходимся по всей топологии узла
        for (Node current : currentNodeTopology.getTopology())
        {
            //если кто-то из нашей топологии отправлял нам этот пакет
            if (current.getAddress().equals(packet.getAddress()) && current.getPort() == packet.getPort())
            {
                //если мы обрабатывали это пакет
                if (current.isExist(sequenceNumber))
                {
                    //если это не отчет о доставке
                    if (message[MSGTYPEPOS] != REPORT)
                        return;

                    //если это отчет о доставке, то удаляем его из неподтвержденных сообзений
                    checkDeliveredMessages(sequenceNumber);
                    return;
                }

                //если мы не обрабатывали пакет, то добавляем его в список обрботанных пакетов
                else
                    current.addMessageID(sequenceNumber);
                break;
            }
        }

        messageAnalyzer(packet);
    }

    // Анализ всех приходящих типов сообщений
    private void messageAnalyzer(DatagramPacket packet) {
        byte[] message = packet.getData();
        ByteBuffer parser = ByteBuffer.wrap(message);
        //номер сообщения
        int sequenceNumber = parser.getInt(SEQNUMPOS);

        //если это отчет о доставке
        if (message[MSGTYPEPOS] == REPORT) {
            checkDeliveredMessages(sequenceNumber);
            return;
        }
        //если это INFO сообщение
        else if (message[MSGTYPEPOS] == INFO) {
            infoMessageAnalyzer(packet, parser, sequenceNumber);
        }
        //если обычное сообщение
        else if (message[MSGTYPEPOS] == TEXT) {
            try {
                //длина текста
                int textLength = parser.getInt(TEXTLENPOS);
                //сообщение
                String chatData = new String(message, TEXTPOS, textLength, "UTF-8");
                System.out.println("Message from [" + currentNodeTopology.getNameByAddress(packet.getAddress(),
                        packet.getPort()) + "]: " + chatData);
                //рассылаем сообщение тем участникам сети, которых мы знаем, но которые не являются отправителем
                sendMessage(chatData, packet.getAddress(), packet.getPort());
            } catch (UnsupportedEncodingException e) {
                System.out.println("[I/O Thread Message] Invalid text data");
            }
        }

        // Автоматически отправяем отчет о доставке о принятом сообщении
        ChatReportMessage reportMessage = new ChatReportMessage(sequenceNumber);
        reportMessage.setRecipient(new Node(packet.getAddress(), packet.getPort()));
        reportMessage.send(socket);
        addWaitingMessage(reportMessage);
    }

    //Анализ INFO сообщений
    private void infoMessageAnalyzer(DatagramPacket packet, ByteBuffer parser, int sequenceNumber) {
        byte[] message = packet.getData();
        //тип информационного сообщения
        byte type = parser.get(INFOTYPEPOS);

        //если это сообщение о новом ребенке / соединении с родителем
        if (type == CHILD || type == PARENT) {
            //создаем новый узел с адресом
            Node newNode = new Node(packet.getAddress(), packet.getPort());
            //добавляем в него номер сообщения
            newNode.addMessageID(sequenceNumber);

            //размер имени
            int nameLength = parser.getInt(INFONAMELENPOS);

            try {
                //установили имя узла
                newNode.setNodeName(new String(message, INFONAMEPOS, nameLength, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                System.out.println("[I/O Thread Message] New node has invalid name");
            }

            //если кто-то подключается к нам как потомок
            if (type == CHILD) {
                //добавили эго в топологию потомков
                currentNodeTopology.addChildrenNode(newNode);
                System.out.println(newNode.getNodeName() + " child is connected");

                // Отправлем потомку сообщение с нашими именем
                ChatInfoMessage toChildMessage = new ChatInfoMessage(PARENT);
                toChildMessage.setNodeName(currentNode.getNodeName());
                toChildMessage.setRecipient(new Node(packet.getAddress(), packet.getPort()));
                toChildMessage.send(socket);
                addWaitingMessage(toChildMessage);
            }
            else if (type == PARENT) {
                //установили нового родителя
                currentNodeTopology.setParent(newNode);
                System.out.println(newNode.getNodeName() + " parent is connected");
            }
        }
        //если это сообщение об отключении родителя, то устанавливаем родителя в null
        else if (type == NOPARENT) {
            currentNodeTopology.setParent(null);
            return;
        }
        //если это сообщение об отключении потомка, то удаляем потомка из коллекции
        else if (type == NOCHILD) {
            if (currentNodeTopology.getChildrens().remove(new Node(packet.getAddress(), packet.getPort())))
                return;
        }
        //если это сообщение о новом родителе
        else if (type == NEWPARENT) {
            try {
                //получии ip-адрес нового родителя
                byte[] rawParentAddr = new byte[4];
                for (int i = 0; i < rawParentAddr.length; i++) {
                    rawParentAddr[i] = parser.get(IPADDRPOS + i);
                }

                InetAddress parentAddr = InetAddress.getByAddress(rawParentAddr);
                System.out.println("New parent IP = " + parentAddr);
                //получили новый порт
                int port = parser.getInt(PORTPOS);
                System.out.println("New parent port = " + port);

                //отправление нашего имени, как имени потомка
                ChatInfoMessage toParentMessage = new ChatInfoMessage(CHILD);
                toParentMessage.setNodeName(currentNode.getNodeName());
                toParentMessage.setRecipient(new Node(parentAddr, port));
                toParentMessage.send(socket);
                addWaitingMessage(toParentMessage);

                currentNodeTopology.getParent().addMessageID(sequenceNumber);
            } catch (UnknownHostException e) {
                System.out.println("[I/O Thread Message] New parent address can't parsed!");
            }
        }
    }

    // В конструкторе создаем второй поток, который будет заниматься принятием и
    // анализом входящих пакетов, а также отправкой сообщений о доставке
    public ChatTree(String[] args) {
        //создали топологию для нашего узла
        currentNodeTopology = new NodeTopology();
        //распарсили аргументы
        parseArguments(args);

        try {
            //создали сокет, отключили broadcast рассылку, и установили время таймаута
            socket = new DatagramSocket(currentNode.getPort());
            socket.setBroadcast(false);
            socket.setSoTimeout(timeout);

            currentNode.setIPAddress(socket.getInetAddress());

            /* Создаем второй поток, который является служебным.
               Его задачи:
                1) Получать сообщения от других узлов
                2) Обрабатывать их
                3) Реализация "надежной доставки сообщений"
            */
            inputThread = new Thread(() -> {
                //создали DatagramPacket для приема пакетов длины maxUDPPacketLength
                DatagramPacket tempPacket = new DatagramPacket(new byte[maxUDPPacketLength], maxUDPPacketLength);
                //для имитации сетевой потери пакета
                Random randGenerator = new Random();
                //таймаут истек
                boolean timeoutExpired = true;

                //пока поток не будет прерван
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        try {
                            //получаем пакет
                            socket.receive(tempPacket);
                            //если отправитель неизвестен и это не информационный пакет, то мы его игнорируем
                            if (!primaryPacketChecking(tempPacket))
                                continue;

                            //таймаут не истек
                            timeoutExpired = false;
                        } catch (SocketTimeoutException e) {}

                        //генерируем число для иммитации сетевой потери пакета
                        int randInt = randGenerator.nextInt(100);
                        //если число больше чем наш процент потерь и мы получили пакет, то кидаем его на обработку
                        if (randInt >= packageLostPercent && !timeoutExpired)
                            messageHandler(tempPacket);

                        deliveryManager();

                        timeoutExpired = true;
                    } catch (IOException e) {
                        System.out.println("[I/O Thread Message] Some problems with packet receiving");
                        System.out.println(e.getMessage());
                    }
                }
            });

            inputThread.start();
        } catch (SocketException e) {
            System.out.println("[ERROR] Some troubles with socket opening! Maybe you enter incorrect port");
        }
    }

    //первичная проверка пакетов, от того ли мы получили пакет
    private boolean primaryPacketChecking(DatagramPacket tempPacket) {
        //getAddress - ip-адрес отправителя
        //getPort - порт отправителя
        Node testNode = new Node(tempPacket.getAddress(), tempPacket.getPort());

        //true, если этот узел есть в топологии нашего узла
        for (Node current : currentNodeTopology.getTopology()) {
            if (current.equals(testNode))
                return true;
        }

        //true, если это INFO пакет и он информирует о новом потомке / соединении с родителем
        if (tempPacket.getData()[MSGTYPEPOS] == INFO) {
            if (tempPacket.getData()[INFOTYPEPOS] == CHILD || tempPacket.getData()[INFOTYPEPOS] == PARENT)
                return true;
        }

        return false;
    }

    // Реализация "надежной доставки сообщений"
    private void deliveryManager() {
        synchronized (waitingReport) {
            //System.out.println("Queue size = " + waitingReport.size());
            Iterator<ChatPacket> packetIter = waitingReport.iterator();

            while (packetIter.hasNext()) {
                ChatPacket currentPacket = packetIter.next();

                if (System.currentTimeMillis() - currentPacket.getLastSendingTime() > timeout) {
                    if (currentPacket.getSendingCount() < MAX_SEND_COUNT)
                        currentPacket.send(socket);
                    else
                        packetIter.remove();
                }
            }

            waitingReport.notifyAll();
        }
    }

    //подключение
    public void connect() {
        connectWithParent();
    }

    // Метод, который блокируется пока, мы не подключились к нашему родителю
    private void connectWithParent() throws ChatInfoMessage.ChatInfoMessageException {

        //ЕСЛИ НЕТ РОДИТЕЛЯ
        if (currentNodeTopology.getParent() == null) {
            System.out.println("We don't have a parent!");
            return;
        }

        //ЕСЛИ ЕСТЬ РОДИТЕЛЬ
        //новое сообщение от ребенка
        ChatInfoMessage packet = new ChatInfoMessage(CHILD);
        //установили получателем родителя
        packet.setRecipient(new Node(currentNodeTopology.getParent().getAddress(), currentNodeTopology.getParent().getPort()));
        //передали наше имя
        packet.setNodeName(currentNode.getNodeName());
        //рассказали родителю о нас
        packet.send(socket);
        //добавили отправленное сообщение в список неподтвержденных
        addWaitingMessage(packet);

        for (;;) {
            try {
                //ждем определенное время
                synchronized (waitingReport) {
                    waitingReport.wait(timeout);
                }

                //если пакет подтвержден
                synchronized (waitingReport) {
                    if (waitingReport.indexOf(packet) == -1)
                        break;
                }
            } catch (InterruptedException e) {}
        }
    }

    //чтение сообщений для отправки + обработка отключения
    public void start() throws IOException {
        //если мы решили отключиться
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            ChatInfoMessage newParentPacket = null;
            ChatInfoMessage noParentPacket = null;

            //Если нет родителя
            if (currentNodeTopology.getParent() == null)
            {
                //ЕСЛИ ЕСТЬ ПОТОМКИ
                if (currentNodeTopology.getChildrens().size() > 0)
                {
                    //новым родителем станет кто-то из детей
                    Node newParent = currentNodeTopology.getChildrens().iterator().next();
                    //создаем INFO сообщение об отсутствии родителя
                    noParentPacket = new ChatInfoMessage(NOPARENT);
                    //получатель - ребенок
                    noParentPacket.setRecipient(new Node(newParent.getAddress(), newParent.getPort()));
                    //отправлем пакет
                    noParentPacket.send(socket);
                    //добавляем в список неподтвержденных
                    addWaitingMessage(noParentPacket);

                    //ЕСЛИ ПОТОМКОВ > 1
                    if (currentNodeTopology.getChildrens().size() > 1)
                    {
                        //создаем список всех потомков
                        Set<Node> childrens = currentNodeTopology.getChildrens();
                        //удаляем из него нового родителя
                        childrens.remove(newParent);
                        //создаем собщение с новым родителем
                        newParentPacket = new ChatInfoMessage(NEWPARENT);
                        //получатели - все дети
                        newParentPacket.setRecipient(childrens);
                        //рассказали про нового родителя
                        newParentPacket.setNewParentNode(newParent);
                        //отправили пакет
                        newParentPacket.send(socket);
                        //добавили в список неподтвержденных
                        addWaitingMessage(newParentPacket);
                    }
                }
            }
            //если у нас есть родитель
            else
            {
                //сообщаем родителю, что у нас отключился потомок
                noParentPacket = new ChatInfoMessage(NOCHILD);
                //посылаем его родителю
                noParentPacket.setRecipient(new Node(currentNodeTopology.getParent().getAddress(),
                        currentNodeTopology.getParent().getPort()));
                noParentPacket.send(socket);
                addWaitingMessage(noParentPacket);

                //если есть дети
                if (currentNodeTopology.getChildrens().size() > 0)
                {
                    //собрали всех детей
                    Set<Node> childrens = currentNodeTopology.getChildrens();
                    //собщение о новом родителе
                    newParentPacket = new ChatInfoMessage(NEWPARENT);
                    newParentPacket.setRecipient(childrens);
                    //новый родитель для детей - наш родитель
                    newParentPacket.setNewParentNode(currentNodeTopology.getParent());
                    newParentPacket.send(socket);
                    addWaitingMessage(newParentPacket);
                }
            }

            // Ждем подтверждения
            synchronized (waitingReport) {
                while (newParentPacket != null && waitingReport.contains(newParentPacket)) {
                    try {
                        waitingReport.wait(1);
                    } catch (InterruptedException e) {}
                }
            }
        }));

        // Основной цикл программы (получение текста для ввода)
        for (;;) {
            String userInput = "";
            System.out.print('[' + currentNode.getNodeName() + "]: ");
            Scanner scan = new Scanner(System.in);
            userInput += scan.nextLine();
            sendMessage(userInput, null, 0);
        }
    }

    // В этой функии происходит отправка текстового сообщения всем получателям (детям и родителю текущего узла)
    private void sendMessage(String userInput, InetAddress address, int port) {
        //записали сообщение
        ChatTextMessage packet = new ChatTextMessage(userInput);
        //коллекция получателей
        Set<Node> recipients = new HashSet<>();

        //если мы написали это сообщение
        if (address == null)
        {
            //если есть родитель
            if (currentNodeTopology.getParent() != null)
                //добавляем родителя в список получателей
                recipients.add(currentNodeTopology.getParent());

            //добавляем всех детей
            recipients.addAll(currentNodeTopology.getChildrens());
        }
        //если сообщение пришло от кого-то
        else
        {
            //добавляем родителя, если сообщение пришло не от него
            if (currentNodeTopology.getParent() != null)
                if (currentNodeTopology.getParent().getAddress().equals(InetAddress.getLoopbackAddress())
                        && currentNodeTopology.getParent().getPort() != port)
                    recipients.add(currentNodeTopology.getParent());
                else if (!currentNodeTopology.getParent().getAddress().equals(address)
                        && currentNodeTopology.getParent().getPort() != port)
                    recipients.add(currentNodeTopology.getParent());

            //добавляем всех детей, кроме того от кого пришло сообщение
            for (Node currentChild : currentNodeTopology.getChildrens()) {
                if (currentChild.getAddress().equals(InetAddress.getLoopbackAddress()) && currentChild.getPort() != port)
                    recipients.add(currentChild);
                else if (!currentChild.getAddress().equals(address) && currentChild.getPort() != port)
                    recipients.add(currentChild);
            }
        }

        //добавлили получателей
        packet.setRecipient(recipients);
        //отправили пакет
        packet.send(socket);
        //ждем подтверждения
        addWaitingMessage(packet);
    }
}

