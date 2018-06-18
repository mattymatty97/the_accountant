package com.accountant;

import jdk.nashorn.internal.runtime.SharedPropertyMap;
import net.dv8tion.jda.core.entities.MessageChannel;

import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class Global {
    public static final String version = "v2.7 - ac prj";
    public static final String build = "1";

    private static Global gbl = new Global();
    public static Global getGbl(){
        return gbl;
    }

    private Map<Long,LogLinker> mapGuild;
    private Map<Long,LogLinker> mapChannel;
    private MessageChannel listener;
    private FileWriter fwGlobal;
    private Map<Long,FileWriter> fwServers;
    private Map<String, String> envMap = System.getenv();

    public Map<String, String> getEnvMap() {
        return envMap;
    }

    public Map<Long, LogLinker> getMapGuild() {
        return mapGuild;
    }

    public Map<Long, LogLinker> getMapChannel() {
        return mapChannel;
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
        mapChannel= new HashMap<>();
        mapGuild= new HashMap<>();
        fwServers=new HashMap<>();
    }
}
