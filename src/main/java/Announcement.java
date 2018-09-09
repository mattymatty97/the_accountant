import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;

import java.util.Comparator;
import java.util.Optional;

public class Announcement {
    public static void main(String[] arguments) throws Exception{

        String message = "";

        JDA api = new JDABuilder(AccountType.BOT).setToken(System.getenv("BOT_TOKEN")).buildBlocking();

        api.getGuilds().forEach(guild -> {
            try {
                Role myrole = guild.getSelfMember().getRoles().stream().filter(Role::isManaged).max(Comparator.comparingLong(Role::getPosition)).orElse(guild.getPublicRole());
                TextChannel channel = Optional.ofNullable(guild.getSystemChannel()).orElse(guild.getDefaultChannel());
                if(channel.canTalk())
                    channel.sendMessage(message.replace("@accountant",myrole.getAsMention())).complete();
                else
                {
                    guild.getOwner().getUser().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(message).complete());
                }
                System.out.println(guild.getName() + " message sent");
            }catch (Exception ignored){}
        });

        api.shutdown();

    }
}
