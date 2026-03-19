package com.capstone;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.staticFiles;

public class CloudStorageApp {

    // A simple, in-memory database to store user credentials (username, password)
    // In a real project, this would be a SQL database.
    private static Map<String, String> userDatabase = new HashMap<>();

    public static void main(String[] args) {
        port(Integer.parseInt(System.getenv("PORT")));
        staticFiles.location("/public"); // Serves index.html

        StorageEngine engine = new StorageEngine();
        Gson gson = new Gson();
        System.out.println("=== ADVANCED CLOUD FILE SYSTEM SERVER STARTED ON http://localhost:4567 ===");

        // --- AUTHENTICATION ROUTES ---

        // Register a new user
        post("/register", (req, res) -> {
            AuthRequest authReq = gson.fromJson(req.body(), AuthRequest.class);
            if (userDatabase.containsKey(authReq.user)) {
                res.status(400);
                return "Error: Username already exists.";
            }
            userDatabase.put(authReq.user, authReq.pass); // Store username and password
            engine.createUserStorage(authReq.user); // Create their root folder
            return "Registration successful.";
        });

        // Log in a user
        post("/login", (req, res) -> {
            AuthRequest authReq = gson.fromJson(req.body(), AuthRequest.class);
            if (!userDatabase.containsKey(authReq.user)) {
                res.status(400);
                return "Error: User not found.";
            }
            if (!userDatabase.get(authReq.user).equals(authReq.pass)) {
                res.status(400);
                return "Error: Invalid password.";
            }
            return "Login successful.";
        });

        // --- FILE SYSTEM ROUTES ---

        // List files and folders at a specific path
        get("/myfiles/:user/:path", (req, res) -> {
            String user = req.params(":user");
            String path = req.params(":path");
            if (path.equals("root")) { path = ""; } // Handle root path

            // Basic security check (prevent ".." in path)
            if (!isPathSafe(path)) {
                res.status(400);
                return "Invalid path.";
            }

            res.type("application/json");
            return gson.toJson(engine.listFiles(user, path));
        });

        // Read (rebuild) a file
        get("/myfile/:user/:path/:filename", (req, res) -> {
            String user = req.params(":user");
            String path = req.params(":path");
            String filename = req.params(":filename");
            if (path.equals("root")) { path = ""; }

            if (!isPathSafe(path) || !isPathSafe(filename)) {
                res.status(400);
                return "Invalid path.";
            }

            return engine.readFile(user, path, filename);
        });

        // Upload a new file
        post("/upload", (req, res) -> {
            String user = req.queryParams("user");
            String path = req.queryParams("path");
            String filename = req.queryParams("filename");

            if (!isPathSafe(path) || !isPathSafe(filename)) {
                res.status(400);
                return "Invalid path.";
            }

            String content = req.body();
            return engine.saveFile(user, path, filename, content);
        });

        // Create a new folder
        post("/create-folder", (req, res) -> {
            FolderRequest folderReq = gson.fromJson(req.body(), FolderRequest.class);
            
            if (!isPathSafe(folderReq.path) || !isPathSafe(folderReq.folderName)) {
                res.status(400);
                return "Invalid folder name.";
            }

            engine.createFolder(folderReq.user, folderReq.path, folderReq.folderName);
            return "Folder created.";
        });
    }
    
    // Helper function for basic path security
    private static boolean isPathSafe(String path) {
        return path != null && !path.contains("..") && !path.contains(":") && !path.startsWith("/");
    }

    // Helper classes to parse JSON from the website
    private static class AuthRequest {
        String user;
        String pass;
    }
    
    private static class FolderRequest {
        String user;
        String path;
        String folderName;
    }
}