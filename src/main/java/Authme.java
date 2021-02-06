import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Playerc;
import mindustry.mod.Plugin;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;

import static mindustry.Vars.netServer;
import static mindustry.Vars.state;

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
            e.player.team(nocore(e.player));
            e.player.unit().kill();
            if(login(e.player)){
                load(e.player);
            } else {
                e.player.sendMessage("You must log-in to play the server. Use the /register and /login commands.");
            }
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Playerc>register("login", "<id> <password>", "Login to account", (arg, player) -> {
            String hashed = BCrypt.hashpw(arg[1], BCrypt.gensalt(11));
            if (login(player, arg[0], hashed)) {
                load(player);
            }
        });
        handler.<Playerc>register("register", "<new_id> <new_password> <password_repeat>", "Login to account", (arg, player) -> {
            try{
                Class.forName("org.mindrot.jbcrypt.BCrypt");
                String hashed = BCrypt.hashpw(arg[1], BCrypt.gensalt(11));
                if(createNewDatabase(player,player.name(),player.uuid(),player.admin(),arg[0],hashed)){
                    load(player);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        });
    }

    public boolean createNewDatabase(Playerc player, String name, String uuid, boolean isAdmin, String id, String pw) throws SQLException {
        if(!check(uuid) && !checkid(id)){
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO 'main','players' ('name','uuid','isadmin','accountid','accountpw') VALUES (?,?,?,?,?)");
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

    public boolean login(Playerc player, String id, String pw) {
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

    public boolean login(Playerc player) {
        try(PreparedStatement stmt = conn.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
            stmt.setString(1, player.uuid());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (rs.getString("uuid").equals(player.uuid())) return true;
                }
            }
        } catch (SQLException e){
            e.printStackTrace();
        }
        return false;
    }

    public void load(Playerc player){
        player.team(state.rules.pvp ? netServer.assignTeam(player.as(), Groups.player) : Team.sharded);
        player.unit().kill();
        player.sendMessage("[green]Login successful!");
    }

    public Team nocore(Playerc player){
        int index = player.team().id+1;
        while (index != player.team().id){
            if (index >= Team.all.length){
                index = 0;
            }
            if (Vars.state.teams.get(Team.all[index]).cores.isEmpty()){
                return Team.all[index];
            }
            index++;
        }
        return player.team();
    }
}
