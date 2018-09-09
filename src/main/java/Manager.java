import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;

public class Manager {
    public static void main(String[] arguments) throws Exception{

        JDA api = new JDABuilder(AccountType.BOT).setToken(System.getenv("BOT_TOKEN")).buildBlocking();

        api.getSelfUser().getManager().setName("Accountant").queue();

        api.shutdown();

    }
}
