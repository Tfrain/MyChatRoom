package com.lucky.thread;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lucky.Client;
import com.lucky.bean.ChatMsg;
import com.lucky.bean.FileMsg;
import com.lucky.bean.Room;
import com.lucky.constant.MsgType;
import com.lucky.constant.ResponseStatus;
import com.lucky.file.ClientFile;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.List;

public class ClientSocketThread extends Thread {

    private static final Logger logger = Logger.getLogger(ClientSocketThread.class);

    private Client client;
    private Socket socket;
    private InputStream is;
    private boolean alive;
    private byte[] buffer = new byte[1024];
    private String message;
    ClientFile clientFile;
    private Gson gson = new Gson();//有各自的gson

    public ClientSocketThread(Client client) {
        this.client = client;
        this.socket = client.getSocket();
        this.is = client.getIs();
        alive = client.isAlive();
        this.clientFile = client.getClientFile();
    }

    @Override
    public void run() {
        while (alive && !client.isSendFile()) {  // 接收客户端socket发送的消息,如果发文件，要关闭这里的监听
            try {
                int len = is.read(buffer);  // 假设数据都是一次读完，不存在组包
                if (len == -1) {  // 客户端socket已经关闭
                    close();
                    break;
                }

                message = new String(buffer, 0, len);
                //logger.info("receive message from server: " + message);
                handlerMsg(message);
            } catch (IOException e) {
                close();
                logger.info("socket closed");
            }
        }
    }

    /**
     * 处理从服务器发来的消息(客户端已经发给服务器信息，服务器处理后再发送)
     * 这是退出房间进行通信的基础，除第一次，其余皆调用此代码，别人的代码可能漏洞百出，群聊就是漏洞百出的
     * 给所有房间内的人打印提示语句
     */
    private void handlerMsg(String msg) {
        if (msg.length() < 2) {
            return;
        }

        char type = msg.charAt(0);
        char status = msg.charAt(1);
        String data = msg.substring(2);
        if (status == ResponseStatus.FAIL) {
            logger.info("request failed, error message: " + data);
            return;
        }

        switch (type) {
            case MsgType.LIST_ROOM:
                List<Room> rooms = gson.fromJson(data, new TypeToken<List<Room>>() {
                }.getType());
                printRooms(rooms);
                break;
            case MsgType.JOIN_ROOM:
                logger.info("joined chat room " + data + " successfully");
                client.setChatRoom(data);
                break;
            case MsgType.QUIT_ROOM:
                logger.info("quit chat room successfully");
                client.setChatRoom("");
                break;
            case MsgType.CREATE_ROOM:
                logger.info("create chat room " + data + " successfully");
                client.setChatRoom(data);
                break;
            case MsgType.CHAT:
                ChatMsg chatMsg = gson.fromJson(data, ChatMsg.class);
                System.out.println(chatMsg.getUser() + ": " + chatMsg.getMsg() + "    " + chatMsg.getDate());
                break;
            case MsgType.SEND_FILE:
                logger.info(data);
                client.setSendFile(true);//所有多设置为true
                if(!client.isSendClient()) {//确认发送文件的客户端不能接收文件,为第一步#7而存在
                    clientFile.getClientFile();
                    System.out.println("接收文件结束，进入正常业务流模式");
                } else {
                    System.out.println("发送客户端不能接收文件流");
                }
                break;
            default:
                logger.info("invalid type");
        }
    }

    private void printRooms(List<Room> rooms) {
        if (rooms == null || rooms.size() == 0) {
            System.out.println("no chat room");
            return;
        }

        System.out.println("--------------------------");
        for (Room room : rooms) {
            System.out.println("Room Name: " + room.getName() + "      User count: " + room.getUserCount());
        }
        System.out.println("--------------------------");
    }

    private void close() {
        logger.info("clientSocket-" + this.getId() + " invoke close(), thread close");
        alive = false;
        client.setAlive(false);
    }
}
