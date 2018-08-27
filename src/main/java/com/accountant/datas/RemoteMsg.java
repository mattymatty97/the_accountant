package com.accountant.datas;

import net.dv8tion.jda.core.entities.Guild;

import java.util.Date;

public class RemoteMsg implements Datas {
    final private String text;
    final private boolean reponse;
    final private Guild guild;
    final private Guild remote;
    final private Date current;

    public RemoteMsg(Date current,String text, Guild guild, Guild remote, boolean reponse) {
        this.text = text;
        this.reponse = reponse;
        this.guild = guild;
        this.remote = remote;
        this.current=current;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public Date getDate() {
        return current;
    }

    public boolean isReponse() {
        return reponse;
    }

    public Guild getGuild() {
        return guild;
    }

    public Guild getRemote() {
        return remote;
    }
}
