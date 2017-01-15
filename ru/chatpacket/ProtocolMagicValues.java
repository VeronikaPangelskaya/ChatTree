package ru.chatpacket;

// Класс, в котором хранятся смещения для разных типов сообщений
// используемых в реализуемом протоколе и прочие константы

public class ProtocolMagicValues {
    public static final byte REPORT = 0;
    public static final byte INFO = 1;
    public static final byte TEXT = 2;

    public static final byte SEQNUMPOS = 1;
    public static  final byte MSGTYPEPOS = 0;
    public static final byte INFOTYPEPOS = 5;
    public static  final byte INFONAMELENPOS = 6;
    public static final byte INFONAMEPOS = 10;
    public static final byte TEXTLENPOS = 5;
    public static final byte TEXTPOS = 9;

    public static final byte IPADDRPOS = 6;
    public static final byte PORTPOS = 10;

    public static final byte CHILD = 0;
    public static final byte PARENT = 1;
    public static final byte NEWPARENT = 2;
    public static final byte NOPARENT = 3;
    public static final byte NOCHILD = 4;
}
