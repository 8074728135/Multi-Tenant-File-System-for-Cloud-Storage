package com.capstone;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import com.google.gson.Gson;

public class StorageEngine {

    // --- GLOBAL STORAGE ---
    // All unique chunks from all users live here.
    private static final String CHUNK_STORAGE_DIR = "storage_chunks"; 
    
    // --- USER-SPECIFIC STORAGE ---
    // Each user gets a folder. Inside, their file "recipes" are stored,
    // mirroring their folder structure.
    private static final String RECIPE_STORAGE_DIR = "storage_recipes"; 
    
    private Gson gson = new Gson();

    public StorageEngine() {
        new File(CHUNK_STORAGE_DIR).mkdirs();
        new File(RECIPE_STORAGE_DIR).mkdirs();
    }
    
    // Called when a user registers
    public void createUserStorage(String owner) {
        new File(RECIPE_STORAGE_DIR, owner).mkdirs();
    }

    // --- HELPER TO GET CORRECT FILE PATHS ---
    private Path getRecipePath(String owner, String path, String fileName) {
        // Creates the full, safe path to a user's recipe file.
        // e.g., storage_recipes/User1/MyProject/notes.txt.json
        return Paths.get(RECIPE_STORAGE_DIR, owner, path, fileName + ".json");
    }
    
    private Path getFolderPath(String owner, String path) {
        // Gets the path to a user's folder.
        // e.g., storage_recipes/User1/MyProject
        return Paths.get(RECIPE_STORAGE_DIR, owner, path);
    }

    // --- CHUNK-BASED SAVE LOGIC (WITH PATHS) ---
    public String saveFile(String owner, String path, String fileName, String content) {
        try {
            Path recipeDir = getFolderPath(owner, path);
            Files.createDirectories(recipeDir); // Ensure parent folder exists

            String[] chunks = content.split("\\s+"); // Split by spaces
            List<String> recipe = new ArrayList<>();
            int newChunksWritten = 0;

            // 1. Process each chunk
            for (String chunk : chunks) {
                if (chunk.isEmpty()) continue; // Skip empty strings
                String chunkHash = DigestUtils.sha256Hex(chunk);
                recipe.add(chunkHash); // Add hash to our "recipe"

                File chunkFile = new File(CHUNK_STORAGE_DIR, chunkHash);

                // 2. Check if this chunk is new
                if (!chunkFile.exists()) {
                    try (FileWriter writer = new FileWriter(chunkFile)) {
                        writer.write(chunk); // Write the new, unique chunk
                    }
                    newChunksWritten++;
                }
            }

            // 3. Save the "recipe" JSON for the user in the correct path
            Path recipeFilePath = getRecipePath(owner, path, fileName);
            try (FileWriter writer = new FileWriter(recipeFilePath.toFile())) {
                gson.toJson(recipe, writer);
            }

            // 4. Report the 80/20 "new data" percentage
            double newPercent = (chunks.length > 0) ? ((double) newChunksWritten / chunks.length) * 100 : 0;
            String report = String.format("New Data Stored: %.2f%%. (Saved %d new chunks)", newPercent, newChunksWritten);
            
            System.out.println("Saved " + fileName + " for " + owner + ". " + report);
            return "[SAVE SUCCESS] " + report;

        } catch (IOException e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    // --- REBUILD FILE FROM CHUNKS (WITH PATHS) ---
    public String readFile(String owner, String path, String fileName) {
        try {
            Path recipeFilePath = getRecipePath(owner, path, fileName);
            
            // 1. Read the recipe
            List<String> recipe = gson.fromJson(new FileReader(recipeFilePath.toFile()), ArrayList.class);
            
            // 2. Rebuild the file
            StringBuilder fileContent = new StringBuilder();
            for (String chunkHash : recipe) {
                String chunkContent = new String(Files.readAllBytes(Paths.get(CHUNK_STORAGE_DIR, chunkHash)));
                fileContent.append(chunkContent).append(" "); // Add space back
            }
            
            return fileContent.toString().trim(); // Return the full, rebuilt file

        } catch (IOException e) {
            return "Error: File not found or corrupt.";
        }
    }

    // --- LIST FILES AND FOLDERS (WITH PATHS) ---
    public Map<String, List<String>> listFiles(String owner, String path) {
        Map<String, List<String>> fileStructure = new HashMap<>();
        fileStructure.put("folders", new ArrayList<>());
        fileStructure.put("files", new ArrayList<>());
        
        File userDir = getFolderPath(owner, path).toFile();
        if (!userDir.exists()) {
            return fileStructure; // No files or folders yet
        }
        
        for (File item : userDir.listFiles()) {
            if (item.isDirectory()) {
                fileStructure.get("folders").add(item.getName());
            } else if (item.getName().endsWith(".json")) {
                fileStructure.get("files").add(item.getName().replace(".json", ""));
            }
        }
        return fileStructure;
    }

    // --- CREATE A NEW FOLDER (WITH PATHS) ---
    public void createFolder(String owner, String path, String folderName) {
        try {
            Path newFolderPath = getFolderPath(owner, path).resolve(folderName);
            Files.createDirectories(newFolderPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}