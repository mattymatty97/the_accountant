package com.accountant.datas;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.utils.Checks;

public class GuildMsg implements Datas{
        final private String text;
        final private boolean reponse;
        final private Guild guild;

    public GuildMsg(String text, Guild guild, boolean reponse) {
        this.text = text;
        this.reponse = reponse;
        Checks.notNull(guild, "guild");
        this.guild = guild;
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
}
