package com.accountant.datas;

import net.dv8tion.jda.core.entities.Guild;

public class RemoteMsg implements Datas {
    final private String text;
    final private boolean reponse;
    final private Guild guild;
    final private Guild remote;

    public RemoteMsg(String text, Guild guild, Guild remote, boolean reponse) {
        this.text = text;
        this.reponse = reponse;
        this.guild = guild;
        this.remote = remote;
    }

    @Override
    public String getText() {
        return text;
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
