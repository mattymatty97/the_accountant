import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.TextChannel;

import java.util.Optional;

public class Announcement {
    public static void main(String[] arguments) throws Exception{

        String message = "";

        JDA api = new JDABuilder(AccountType.BOT).setToken(System.getenv("BOT_TOKEN")).buildBlocking();

        api.getGuilds().forEach(guild -> {
            try {
                TextChannel channel = Optional.ofNullable(guild.getSystemChannel()).orElse(guild.getDefaultChannel());
                if(channel.canTalk())
                    channel.sendMessage(message).complete();
                else
                {
                    guild.getOwner().getUser().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(message).complete());
                }
            }catch (Exception ignored){}
        });

        api.shutdown();

    }
}
