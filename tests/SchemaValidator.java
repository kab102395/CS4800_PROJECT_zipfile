import java.sql.*;
import java.util.Arrays;
import java.util.List;

public class SchemaValidator {
    public static void main(String[] args) throws Exception {
        String dbPath = "../tictactoe/java-server/database/ttt_game";
        String url = "jdbc:h2:" + dbPath + ";MODE=MySQL;AUTO_SERVER=TRUE";
        List<String> tables = Arrays.asList(
            "USERS","USER_GAME_SCORES","USER_GAME_STATS","PLAYER_SESSIONS","GAME_MATCHES",
            "GAME_MOVES","PENDING_NOTIFICATIONS","CONNECTION_HEALTH","LOBBY_STATE",
            "PLAYER_STATS","SCHEMA_VERSION"
        );
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            DatabaseMetaData meta = conn.getMetaData();
            System.out.println("Validating tables...");
            for (String t : tables) {
                try (ResultSet rs = meta.getTables(null, null, t, null)) {
                    if (!rs.next()) {
                        throw new RuntimeException("Missing table: " + t);
                    }
                }
            }
            System.out.println("Tables OK");

            System.out.println("Checking columns for USERS...");
            requireCols(meta, "USERS", new String[]{"USER_ID","USERNAME","PASSWORD_HASH","IS_ACTIVE"});
            System.out.println("Checking columns for USER_GAME_SCORES...");
            requireCols(meta, "USER_GAME_SCORES", new String[]{"SCORE_ID","USER_ID","GAME_NAME","SCORE","LEVEL"});
            System.out.println("Checking columns for USER_GAME_STATS...");
            requireCols(meta, "USER_GAME_STATS", new String[]{"STAT_ID","USER_ID","GAME_NAME","TOTAL_PLAYS","BEST_SCORE"});

            System.out.println("Sample join...");
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM USER_GAME_SCORES s JOIN USERS u ON s.USER_ID=u.USER_ID")) {
                ps.executeQuery();
            }
            System.out.println("Sample join OK");

            System.out.println("Schema validation PASS");
        }
    }

    private static void requireCols(DatabaseMetaData meta, String table, String[] cols) throws Exception {
        for (String c : cols) {
            try (ResultSet rs = meta.getColumns(null, null, table, c)) {
                if (!rs.next()) {
                    throw new RuntimeException("Missing column " + c + " in " + table);
                }
            }
        }
    }
}
