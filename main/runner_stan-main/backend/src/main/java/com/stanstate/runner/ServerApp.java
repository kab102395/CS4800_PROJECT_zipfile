package com.stanstate.runner;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import spark.Filter;
import spark.Spark;

import java.util.List;
import java.util.Map;

public class ServerApp {
    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        String dbPath = System.getenv().getOrDefault("RUNNER_DB", "jdbc:sqlite:runs.db");

        Database database = new Database(dbPath);
        database.initialize();

        Spark.port(port);
        enableCors("*", "*", "*");

        Spark.get("/api/runs", (req, res) -> {
            List<RunRecord> runs = database.listTopRuns(10);
            res.type("application/json");
            return GSON.toJson(Map.of("runs", runs));
        });

        Spark.post("/api/runs", (req, res) -> {
            RunRequest body = parseBody(req.body());
            if (body.score < 0 || body.coins < 0) {
                res.status(400);
                return GSON.toJson(Map.of("error", "Score and coins must be non-negative"));
            }
            RunRecord record = database.insertRun(body.player, body.score, body.coins);
            res.status(201);
            res.type("application/json");
            return GSON.toJson(Map.of("run", record));
        });

        Spark.awaitInitialization();
        System.out.printf("Runner backend started on port %d%n", port);
    }

    private static RunRequest parseBody(String body) {
        try {
            RunRequest parsed = GSON.fromJson(body, RunRequest.class);
            if (parsed == null) {
                throw new IllegalArgumentException("Body required");
            }
            return parsed;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }

    private static void enableCors(final String origin, final String methods, final String headers) {
        Spark.options("/*", (request, response) -> {
            String reqHeaders = request.headers("Access-Control-Request-Headers");
            if (reqHeaders != null) {
                response.header("Access-Control-Allow-Headers", reqHeaders);
            }

            String reqMethod = request.headers("Access-Control-Request-Method");
            if (reqMethod != null) {
                response.header("Access-Control-Allow-Methods", reqMethod);
            }
            return "OK";
        });

        Filter filter = (request, response) -> {
            response.header("Access-Control-Allow-Origin", origin);
            response.header("Access-Control-Allow-Methods", methods);
            response.header("Access-Control-Allow-Headers", headers);
        };

        Spark.before(filter);
        Spark.after(filter);
    }

    private static class RunRequest {
        String player;
        int score;
        int coins;
    }
}

