package com.accountant;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.MessageChannel;

import java.util.Map;

public class LogLinker {
    private long guildId;
    private long channelId;
    private MessageChannel channel;
    private EmbedBuilder message;

    public synchronized long getGuildId() {
        return guildId;
    }

    public synchronized long getChannelId() {
        return channelId;
    }

    public synchronized MessageChannel getChannel() {
        return channel;
    }

    public synchronized EmbedBuilder getMessage() {
        return message;
    }

    public LogLinker(long guildId,MessageChannel channel){
        synchronized (this) {
            this.channel = channel;
            this.channelId = channel.getIdLong();
            this.guildId = guildId;
            message = new EmbedBuilder();

            Map<Long, LogLinker> map1 = Global.getGbl().getMapGuild();

            Map<Long, LogLinker> map2 = Global.getGbl().getMapChannel();

            map1.put(guildId, this);

            map2.put(channelId, this);
        }
    }

    public synchronized void delete(){
        Map<Long,LogLinker> map1 = Global.getGbl().getMapGuild();

        Map<Long,LogLinker> map2 = Global.getGbl().getMapChannel();

        map1.remove(guildId);

        map2.remove(channelId);
    }


}
