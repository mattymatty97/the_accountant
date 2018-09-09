package com.accountant;


import net.dv8tion.jda.core.entities.MessageChannel;

import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

@SuppressWarnings("WeakerAccess")
public class Global {
    public static final String version = "v3.1 - ac prj";
    public static final String build = "3";

    private static Global gbl = new Global();

    public static Global getGbl(){
        return gbl;
    }

    public static final Queue<Integer> eventQueue = new PriorityQueue<>();
    public static int maxEventCtn = 1;

    private MessageChannel listener;
    private FileWriter fwGlobal;
    private Map<Long,FileWriter> fwServers;
    private Map<String, String> envMap = System.getenv();

    public Map<String, String> getEnvMap() {
        return envMap;
    }

    public MessageChannel getListener() {
        return listener;
    }

    public void setListener(MessageChannel listener) {
        this.listener = listener;
    }

    public FileWriter getFwGlobal() {
        return fwGlobal;
    }

    public void setFwGlobal(FileWriter global) {
        this.fwGlobal = global;
    }

    public Map<Long, FileWriter> getFwServers() {
        return fwServers;
    }

    private Global(){
        fwServers=new HashMap<>();
    }
}
