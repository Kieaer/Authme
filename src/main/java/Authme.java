import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.plugin.Plugin;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;

import static mindustry.Vars.*;

public class Authme extends Plugin {
    Connection conn;

    public Authme(){
        try {
            Class.forName("org.sqlite.JDBC");
            Class.forName("org.mindrot.jbcrypt.BCrypt");
            conn = DriverManager.getConnection("jdbc:sqlite:" + Core.settings.getDataDirectory().child("player.sqlite3").absolutePath());

            String sql = "CREATE TABLE IF NOT EXISTS players(\n"+
                    "id INTEGER PRIMARY KEY AUTOINCREMENT,\n"+
                    "name TEXT,\n"+
                    "uuid TEXT,\n"+
                    "isadmin TEXT,\n"+
                    "accountid TEXT,\n"+
                    "accountpw TEXT\n"+
                    ");";
            try (Statement stmt = conn.createStatement()){
                stmt.execute(sql);
            }
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }

        Events.on(EventType.PlayerJoin.class, e->{
            e.player.setTeam(nocore(e.player));
            Call.onPlayerDeath(e.player);
            if(login(e.player)){
                load(e.player);
            } else {
                e.player.sendMessage("You must log-in to play the server. Use the /register and /login commands.");
            }
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("login", "<id> <password>", "Login to account", (arg, player) -> {
            String hashed = BCrypt.hashpw(arg[1], BCrypt.gensalt(11));
            if (login(player, arg[0], hashed)) {
                load(player);
            }
        });
        handler.<Player>register("register", "<new_id> <new_password> <password_repeat>", "Login to account", (arg, player) -> {
            try{
                Class.forName("org.mindrot.jbcrypt.BCrypt");
                String hashed = BCrypt.hashpw(arg[1], BCrypt.gensalt(11));
                if(createNewDatabase(player,player.name,player.uuid,player.isAdmin,arg[0],hashed)){
                    load(player);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        });
    }

    public boolean createNewDatabase(Player player, String name, String uuid, boolean isAdmin, String id, String pw) throws SQLException {
        if(!check(uuid) && !checkid(id)){
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO 'players' ('name','uuid','isadmin','accountid','accountpw') VALUES (?,?,?,?,?)");
            stmt.setString(1,name);
            stmt.setString(2,uuid);
            stmt.setBoolean(3,isAdmin);
            stmt.setString(4,id);
            stmt.setString(5,pw);
            stmt.execute();
            stmt.close();
            return true;
        } else {
            player.sendMessage("This account is already in use!");
            return false;
        }
    }

    public boolean check(String uuid) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM players WHERE uuid = ?");
        stmt.setString(1,uuid);
        ResultSet rs = stmt.executeQuery();
        boolean result = rs.next();
        rs.close();
        stmt.close();
        return result;
    }

    public boolean checkid(String id) {
        try(PreparedStatement stmt = conn.prepareStatement("SELECT * FROM players WHERE accountid = ?")) {
            stmt.setString(1, id);
            try(ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e){
            e.printStackTrace();
        }
        return false;
    }

    public boolean login(Player player, String id, String pw) {
        try(PreparedStatement stmt = conn.prepareStatement("SELECT * FROM players WHERE accountid = ?, accountpw = ?")) {
            stmt.setString(1, id);
            stmt.setString(2, pw);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (BCrypt.checkpw(pw, rs.getString("pw"))) return true;
                } else {
                    player.sendMessage("Login failed! Check your account id or password!");
                }
            }
        } catch (SQLException e){
            e.printStackTrace();
        }
        return false;
    }

    public boolean login(Player player) {
        try(PreparedStatement stmt = conn.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
            stmt.setString(1, player.uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (rs.getString("uuid").equals(player.uuid)) return true;
                }
            }
        } catch (SQLException e){
            e.printStackTrace();
        }
        return false;
    }

    public void load(Player player){
        player.setTeam(state.rules.pvp ? netServer.assignTeam(player, playerGroup.all()) : Team.sharded);
        Call.onPlayerDeath(player);
        player.sendMessage("[green]Login successful!");
    }

    public Team nocore(Player player){
        int index = player.getTeam().id+1;
        while (index != player.getTeam().id){
            if (index >= Team.all().length){
                index = 0;
            }
            if (Vars.state.teams.get(Team.all()[index]).cores.isEmpty()){
                return Team.all()[index];
            }
            index++;
        }
        return player.getTeam();
    }
}
