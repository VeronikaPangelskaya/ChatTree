package ru;

import java.io.IOException;

public class Main {
    //Параметры:
        //имя узла
        //собственный порт
        //процент потерь
        //ip-адрес родителя
        //порт родителя
    public static void main(String[] args) throws IOException {
        ChatTree chat = new ChatTree(args);
        chat.connect();
        chat.start();
    }
}
