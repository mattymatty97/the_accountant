package com.accountant;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class Pinger implements Runnable {
    long id;
    String ip;
    int port;
    @Override
    public void run() {
        try{
            while (!Thread.interrupted()) {
                try {
                    DatagramSocket clientSocket = new DatagramSocket();
                    InetAddress IPAddress = InetAddress.getByName(ip);
                    byte[] sendData = longToBytes(id);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, Long.BYTES , IPAddress, port);
                    clientSocket.send(sendPacket);
                    clientSocket.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                Thread.sleep(13000);
            }
        }catch (InterruptedException ignored){}
    }

    public Pinger(long id) {
        this.id = id;
        String[] ip = System.getenv("LISTENER_IP").split(":");
        this.ip = ip[0];
        this.port = Integer.parseInt(ip[1]);
    }

    public byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }
}
