import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class GitRepository {
    private static Path gitdir = null;

    public GitRepository() {
        Path worktree = Paths.get(System.getProperty("user.dir"));
        this.gitdir = worktree.resolve(".gitz");
    }

    public static String objectFind(Path path, String name, String fmt, boolean follow) throws Exception {
        List<String> sha = objectResolve(path, name);

        System.out.println("finding obj: "+name);
        System.out.println("resolved candidates: "+sha);

        if(sha.isEmpty()){
            throw new Exception("no such reference "+name);
        }

        if(sha.size() > 1){
            throw new Exception("ambiguous reference " + name + ": candidates are:\n -" + String.join("\n - ",sha));
        }

        String resolvedSha = sha.getFirst();

        if(fmt == null) return resolvedSha;

        while(true){
            // to check type of obj
            GitObject obj = objectRead(resolvedSha);
            // not performant

            if(obj == null) throw new Exception("obj not found");

            if(obj.getFmt().equals(fmt)){
                return resolvedSha;
            }

            if(!follow) return null;

            if (obj.getFmt().equals("tag")) {
                GitTag tag = (GitTag) obj;
                Map<byte[], byte[]> kvlm = tag.getKvlm();
                for (Map.Entry<byte[], byte[]> entry : kvlm.entrySet()) {
                    if (entry.getKey() != null && new String(entry.getKey(), StandardCharsets.UTF_8).equals("object")) {
                        resolvedSha = new String(entry.getValue(), StandardCharsets.UTF_8);
                        break;
                    }
                }
            } else if (obj.getFmt().equals("commit") && fmt.equals("tree")) {
                GitCommit commit = (GitCommit) obj;
                Map<byte[], byte[]> kvlm = commit.getKvlm();
                for (Map.Entry<byte[], byte[]> entry : kvlm.entrySet()) {
                    if (entry.getKey() != null && new String(entry.getKey(), StandardCharsets.UTF_8).equals("tree")) {
                        resolvedSha = new String(entry.getValue(), StandardCharsets.UTF_8);
                        break;
                    }
                }
            } else {
                return null;
            }
        }
    }

    public static List<String> objectResolve(Path repo, String name) throws IOException {
        List<String> candidates = new ArrayList<>();
        System.out.println("resolving reference: "+name);
        Pattern hashPattern = Pattern.compile("^[0-9A-Fa-f]{4,40}$");

        // empty string? abort
        if(name == null || name.trim().isEmpty()){
            System.out.println("empty reference name");
            return candidates;
        }

        if(name.equals("HEAD")){
            String headRef = GitUtils.refResolve(repo, Path.of(".gitz/HEAD"));
            if(headRef != null){
                candidates.add(headRef);
            }
            return candidates;
        }

        if(hashPattern.matcher(name).matches()){
            String prefix = name.substring(0,2);
            Path path = repo.resolve(".gitz/objects/"+prefix);

            if(Files.exists(path)){
                String rem = name.substring(2);
                File[] files = path.toFile().listFiles();
                if (files != null){
                    for(File f: files){
                        String fileName = f.getName();
                        if(fileName.startsWith(rem)){
                            System.out.println("match found");
                            candidates.add(prefix + fileName);
                        }
                    }
                }
            }
        }

        String asTag = GitUtils.refResolve(repo, Path.of("refs/tags"+name));
        if(asTag != null){
            candidates.add(asTag);
        }

        String asBranch = GitUtils.refResolve(repo, Path.of("refs/heads"+name));
        if(asBranch != null){
            candidates.add(asBranch);
        }

        System.out.println("resolved candidates returned from objRes: "+candidates);
        return candidates;
    }

    public static GitObject objectRead(String sha){
        Path path = repoPath("objects", sha.substring(0,2), sha.substring(2));
        File file = path.toFile();
        if(!file.exists()){
            System.out.println("file doesn't exist");
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file)){
            InflaterInputStream iis = new InflaterInputStream(fis);

            byte[] raw = iis.readAllBytes();
            System.out.println("raw obj length: "+raw.length);

            int x = indexOf(raw, (byte) ' ');
            if(x == -1) throw new RuntimeException("malformed object: no obj found");

            byte[] fmtBytes = new byte[x];
            System.arraycopy(raw, 0, fmtBytes, 0, x);
            String fmt = new String(fmtBytes, StandardCharsets.US_ASCII);
            System.out.println("obj format: "+fmt);

            int y = indexOf(raw, (byte) 0, x);
            if(y == -1) throw new RuntimeException("malformed object: no null char found");

            String sizeStr = new String(raw, x+1, y-(x+1), StandardCharsets.US_ASCII).trim();
            int size = Integer.parseInt(sizeStr);
            if(size != raw.length- y - 1){
                throw new RuntimeException("malformed object "+sha+": bad length");
            }
            System.out.println("obj size: +"+size);

            Class<? extends GitObject> c = null;
            switch (fmt){
                case "commit" -> c = GitCommit.class;
                case "tree" -> c = GitTree.class;
                case "tag" -> c = GitTag.class;
                case "blob" -> c = GitBlob.class;
                default -> System.out.println("idk man");
            }

            byte[] data = new byte[size];
            System.arraycopy(raw, y+1, data, 0, size);
            assert c != null;
            System.out.println("obj deserialized");
            return c.getDeclaredConstructor(byte[].class).newInstance((Object) data);
        }
        catch (Exception e){
            e.getMessage();
            e.printStackTrace();
            return null;
        }
    }

    public static String objectHash(String filePath, String type, Path repo) throws Exception {
        byte[] data = Files.readAllBytes(Paths.get(filePath));

        GitObject obj = new GitBlob();
        switch (type) {
            case "commit" ->
                obj = new GitCommit(data);
            case "tree" ->
                obj = new GitTree(data);
            case "tag" ->
                obj = new GitTag(data);
            case "blob" ->
                obj = new GitBlob(data);
            default ->
                System.out.println("unknown type");
        }

        if(repo != null){
            return objectWrite(obj);
        }
        else{
            return computeSHA(obj.serialize());
        }
    }

    public static String objectWrite(GitObject obj) throws IOException, NoSuchAlgorithmException {
        byte[] data = obj.serialize();

        String header = obj.getFmt() + " " + data.length + "\0";
        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);

        byte[] result = new byte[headerBytes.length + data.length];
        System.arraycopy(headerBytes, 0, result, 0,headerBytes.length);
        System.arraycopy(data, 0, result, headerBytes.length, data.length);

        String sha = computeSHA(result);

        Path path = repoPath("objects", sha.substring(0,2), sha.substring(2));

        if(!path.getParent().toFile().exists()){
            path.getParent().toFile().mkdirs();
        }

        if(!path.toFile().exists()){
            try (FileOutputStream fos = new FileOutputStream(path.toFile())){
                DeflaterOutputStream dos = new DeflaterOutputStream(fos);
                dos.write(result);
                dos.finish();
                dos.flush();
            }
        }

        return sha;
    }

    static String computeSHA(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hashBytes = digest.digest(data);
        StringBuilder hexString = new StringBuilder();

        for (byte b: hashBytes){
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1){
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }

    private static int indexOf(byte[] array, byte target) {
        return indexOf(array, target, 0);
    }

    private static int indexOf(byte[] array, byte target, int start) {
        for (int i = start; i < array.length; i++) {
            if (array[i] == target) {
                return i;
            }
        }
        return -1;
    }

    public void gitInit() throws Exception {
        if(gitdir.toFile().exists()) {
            System.out.println("already a gitz repo");
        }
        else {

            if(gitdir.toFile().mkdir()) {
                repoDir(new String[]{"branches"}, true);
                repoDir(new String[]{"objects"}, true);
                repoDir(new String[]{"refs", "tags"}, true);
                repoDir(new String[]{"refs", "heads"},true);

                writeFile(new String[]{"HEAD"}, "ref: refs/heads/master\n"); //master as linus intended
                writeFile(new String[]{"description"}, "unnamed repo, edit this file to name repo.\n");
                writeFile(new String[]{"config"}, repoDefaultConfig().toString());
                writeFile(new String[]{"info", "exclude"}, "");

                System.out.println("gitz repo initialized");
            }

            else {
                System.out.println("failed to init gitz repo");
            }
        }
    }

    // computes the path
    public static Path repoPath(String... path){
        return gitdir.resolve(Paths.get("", path));
    }

    private Path repoFile(String[] path, boolean mkdir) throws Exception {
        if(repoDir(path, mkdir) == null)
            return repoPath(path);
        return null;
    }

    private Path repoDir(String[] path, boolean mkdir) throws Exception {
        Path resolevedPath = repoPath(path);

        if(resolevedPath.toFile().exists()){
            if(resolevedPath.toFile().isDirectory())
                return resolevedPath;
            else
                throw new Exception("not a directory");
        }

        if (mkdir){
            if(resolevedPath.toFile().mkdirs())
                return resolevedPath;
        }
        return null;
    }

    private void writeFile(String[] path, String content) throws Exception {
        Path filePath = repoFile(path, false);
        File newFile = filePath.toFile();
        try (FileWriter fw = new FileWriter(newFile)){
            fw.write(content);
        }
    }

    // why am i even making this func, this could be string contents bruh
    private Properties repoDefaultConfig() throws Exception {
        Properties config = new Properties();

        config.setProperty("core.repositoryformatversion", "0");
        config.setProperty("core.filemode","false");
        config.setProperty("core.bare","false");

        return config;
    }

    public static Path repoFind(String path, boolean required) {
        File dir = new File(path).getAbsoluteFile();

        if (new File(dir, ".gitz").isDirectory()) {
            gitdir = dir.toPath().resolve(".gitz");
            return dir.toPath();
        }

        File parent = dir.getParentFile();
        if (parent == null || dir.equals(parent)) {
            if (required) {
                throw new RuntimeException("not a gitz dir");
            } else {
                return null;
            }
        }

        return repoFind(parent.getAbsolutePath(), required);
    }


    public static void rm(Path repo, List<Path> paths, boolean delete, boolean skipMissing) throws Exception {
        GitIndex index = GitIndex.readFromFile(repo.resolve(".gitz/index").toFile());

        String worktreePath = repo.toString() + FileSystems.getDefault().getSeparator();

        // make paths absolute
        Set<Path> absPaths = new HashSet<>();
        for (Path path : paths) {
            Path absPath = path.toAbsolutePath();
            if (absPath.startsWith(repo)) {
                absPaths.add(absPath);
            } else {
                throw new RuntimeException("cannot remove paths outside of worktree: " + path);
            }
        }

        // the list of entries to keep, which we will write back to the index
        List<GitIndexEntry> keptEntries = new ArrayList<>();
        // the list of removed paths, will use after index update to physically remove the actual paths from the filesystem type sht
        List<Path> remove = new ArrayList<>();

        // preserve the others in keptEntries
        for (GitIndexEntry e : index.getEntries()) {
            Path fullPath = repo.resolve(e.getName());

            if (absPaths.contains(fullPath)) {
                remove.add(fullPath);
                absPaths.remove(fullPath);
            } else {
                keptEntries.add(e); // preserve entry
            }
        }

        // if absPaths is not empty, it means some paths weren't in the index
        if (!absPaths.isEmpty() && !skipMissing) {
            throw new RuntimeException("cannot remove paths not in the index: " + absPaths);
        }

        // physically delete paths from filesystem
        if (delete) {
            for (Path path : remove) {
                Files.deleteIfExists(path);
            }
        }

        // update the list of entries in the index, and write it back
        index.setEntries(keptEntries);
        GitIndex.indexWrite(repo, index);
    }

    public static void add(Path repo, List<Path> paths, boolean delete, boolean skipMissing) throws Exception {
        System.out.println("adding files: " + paths); // debug log

        // remove existing entries from the index
        rm(repo, paths, delete, true);

        AtomicReference<Path> worktree = new AtomicReference<>(repo);
        GitIndex index = GitIndex.readFromFile(repo.resolve(".gitz/index").toFile());
        Map<String, GitIndexEntry> entryMap = new HashMap<>();

        // load existing entries into a map
        for (GitIndexEntry entry : index.getEntries()) {
            entryMap.put(entry.getName(), entry);
        }

        for (Path path : paths) {
            Path absPath = path.toAbsolutePath();
            if (!absPath.startsWith(worktree.get()) || !Files.isRegularFile(absPath)) {
                throw new RuntimeException("Not a file or outside the worktree: " + path);
            }

            byte[] fileContent = Files.readAllBytes(absPath);
            GitBlob blob = new GitBlob(fileContent);
            String sha = objectWrite(blob);
            System.out.println("Generated hash for " + path + ": " + sha);

            BasicFileAttributes attrs = Files.readAttributes(absPath, BasicFileAttributes.class);

            int modeType = Files.isSymbolicLink(absPath) ? 0b1010 : 0b1000; // symlink or regular file
            int modePerms = Files.isExecutable(absPath) ? 0755 : 0644;

            String cleanName = worktree.get().relativize(absPath).toString().replace("\0", "");

            GitIndexEntry indexEntry = new GitIndexEntry(
                    new long[]{attrs.creationTime().toMillis() / 1000, 0},
                    new long[]{attrs.lastModifiedTime().toMillis() / 1000, 0},
                    0, 0, // dev and inode set to 0
                    modeType, modePerms,
                    0, 0, // uid and gid set to 0
                    attrs.size(),
                    sha, false, 0,
                    cleanName
            );

            entryMap.put(indexEntry.getName(), indexEntry); // update or add entry
        }

        // update index with all entries
        index.setEntries(new ArrayList<>(entryMap.values()));
        GitIndex.indexWrite(repo, index);
        System.out.println("index updated successfully.");
    }


    public static String objectHash(byte[] data, String type, Path repo) throws Exception {
        GitObject obj = new GitBlob(data);
        return GitRepository.objectWrite(obj);
    }

    public static String treeFromIndex(Path repo, GitIndex index) throws Exception {
        // map to store directories and their contents
        Map<String, List<Object>> contents = new HashMap<>();
        contents.put("", new ArrayList<>());

        // enumerate entries and organize them by directory
        for (GitIndexEntry entry : index.getEntries()) {
            String dirname = new File(entry.getName()).getParent();
            if (dirname == null) dirname = "";

            // create dictionary entries up to root
            String key = dirname;
            while (!key.isEmpty()) {
                if (!contents.containsKey(key)) {
                    contents.put(key, new ArrayList<>());
                }
                // get parent directory
                File keyFile = new File(key);
                key = keyFile.getParent() != null ? keyFile.getParent() : "";
            }

            // store the entry in the list
            contents.get(dirname).add(entry);
        }

        // sort directories by length in descending order
        List<String> sortedPaths = new ArrayList<>(contents.keySet());
        sortedPaths.sort((a, b) -> Integer.compare(b.length(), a.length()));

        // variable to store the current tree's SHA-1
        String sha = null;

        // process directories from deepest to shallowest
        for (String path : sortedPaths) {
            // prepare a new empty tree object
            GitTree tree = new GitTree();
            tree.init();

            // add each entry to the new tree
            for (Object entry : contents.get(path)) {
                if (entry instanceof GitIndexEntry) {
                    // regular file entry
                    GitIndexEntry indexEntry = (GitIndexEntry) entry;

                    // format mode: combine type and permissions
                    String modeStr = String.format("%02o%04o", indexEntry.getModeType(), indexEntry.getModePerms());
                    byte[] modeBytes = modeStr.getBytes(StandardCharsets.US_ASCII);

                    // get the basename of the file
                    String baseName = new File(indexEntry.getName()).getName();

                    // create tree leaf
                    GitTreeLeaf leaf = new GitTreeLeaf(modeBytes, Path.of(baseName), indexEntry.getSha());
                    tree.getItems().add(leaf);
                } else {
                    // tree entry
                    Object[] treeEntry = (Object[]) entry;
                    String baseName = (String) treeEntry[0];
                    String treeSha = (String) treeEntry[1];

                    // create tree leaf with directory mode (040000)
                    GitTreeLeaf leaf = new GitTreeLeaf("040000".getBytes(StandardCharsets.US_ASCII),
                            Path.of(baseName), treeSha);
                    tree.getItems().add(leaf);
                }
            }

            // write the new tree object to the store
            sha = GitRepository.objectWrite(tree);

            // Add the new tree hash to the parent directory's content list
            String parent = new File(path).getParent();
            if (parent == null) parent = "";
            String base = new File(path).getName();

            // store as object[] {basename, SHA} to distinguish from GitIndexEntry
            contents.get(parent).add(new Object[] {base, sha});
        }

        return sha;
    }
}