import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class GitStatus {
    // find the active branch
    public static String branchGetActive(Path repo) throws IOException {
        Path headFile = repo.resolve(".gitz/HEAD");
        String head = Files.readString(headFile, StandardCharsets.UTF_8).trim();

        if(head.startsWith("ref: refs/heads")){
            return head.substring(16);
        }
        else{
            return null;
        }
    }

    public static Map<String, String> treeToDict(Path repo, String ref, String prefix) throws Exception {
        Map<String, String> ret = new HashMap<>();
        String treeSha = GitRepository.objectFind(repo, ref, "tree", true);
        GitObject obj = GitRepository.objectRead(treeSha);

        if(!(obj instanceof GitTree)){
            throw new Exception("expected a tree object");
        }

        for(GitTreeLeaf leaf : ((GitTree) obj).getItems()) {
            String fullPath = leaf.getPath().getFileName().toString();
            if(new String(leaf.getMode(), StandardCharsets.UTF_8).startsWith("04")){
                // its a tree (directory), recurse
                ret.putAll(treeToDict(repo, leaf.getSha(), fullPath));
            }else{
                // its a file (blob)
                ret.put(fullPath, leaf.getSha());
            }
        }

        return ret;
    }

    public static void cmdStatusHeadIndex(Path repo, GitIndex index) throws Exception {
        System.out.println("changes to commited:");
        Map<String, String> head = treeToDict(repo, "HEAD", "");

        for(GitIndexEntry entry: index.getEntries()) {
            String fileName = entry.getName();

            if (head.containsKey(fileName)) {
                if (!head.get(fileName).equals(entry.getSha())) {
                    System.out.println("  modified " + fileName);
                }
                head.remove(fileName); // file is already tracked, remove from HEAD
            }
            else{
                System.out.println("  added:   "+fileName);
            }
        }

        // remaining files in HEAD are deleted
        for(String deletedFile : head.keySet()){
            System.out.println("  deleted:  "+deletedFile);
        }
    }

    public static void cmdStatusIndexWorktree(Path repo, GitIndex index) throws Exception {
        System.out.println("changes not staged for commit:");

        GitIgnore ignore = GitIgnore.gitIgnoreRead(repo);
        List<String> allFiles = new ArrayList<>();
        Map<String, GitIndexEntry> indexMap = new HashMap<>();
        for (GitIndexEntry entry : index.getEntries()) {
            indexMap.put(entry.getName(), entry);
        }

        // traverse filesystem
        File worktree = repo.toFile();
        Queue<File> queue = new LinkedList<>();
        queue.add(worktree);

        while (!queue.isEmpty()) {
            File dir = queue.poll();
            File[] files = dir.listFiles();
            if (files == null) continue;

            for (File f : files) {
                if (f.isDirectory()) {
                    queue.add(f);
                } else {
                    String relativePath = worktree.toPath().relativize(f.toPath()).toString();
                    allFiles.add(relativePath);
                }
            }
        }

        // compare index with filesystem
        for (GitIndexEntry entry : index.getEntries()) {
            String filePath = entry.getName();
            File file = new File(repo.toFile(), filePath);

            if (!file.exists()) {
                System.out.println("  deleted:   " + filePath);
            } else {
                byte[] fileContent = Files.readAllBytes(file.toPath());
                String newSha = GitRepository.computeSHA(fileContent);

                if (!newSha.equals(entry.getSha())) {
                    System.out.println("  modified: " + filePath);
                }
            }
            allFiles.remove(filePath);
        }

        System.out.println();
        System.out.println("untracked files:");
        for (String file : allFiles) {
            if (!GitIgnore.checkIgnore(ignore, file) && !file.startsWith(".gitz/")) {
                System.out.println("  " + file);
            }
        }
    }
}