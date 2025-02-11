import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class GitRepository {
    private final Path worktree;
    private final Path gitdir;

    public GitRepository() {
        this.worktree = Paths.get(System.getProperty("user.dir"));
        this.gitdir = worktree.resolve(".gitz");
    }

    public GitObject objectRead(String sha){
        Path path = repoPath("objects", sha.substring(0,2), sha.substring(2));
        File file = path.toFile();
        if(!file.exists()){
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file)){
            InflaterInputStream iis = new InflaterInputStream(fis);

            byte[] raw = iis.readAllBytes();

            int x = indexOf(raw, (byte) ' ');
            if(x == -1) throw new RuntimeException("malformed object: no obj found");

            byte[] fmtBytes = new byte[x];
            System.arraycopy(raw, 0, fmtBytes, 0, x);
            String fmt = new String(fmtBytes, StandardCharsets.US_ASCII);

            int y = indexOf(raw, (byte) 0, x);
            if(y == -1) throw new RuntimeException("malformed object: no null char found");

            String sizeStr = new String(raw, x+1, y-x+1, StandardCharsets.US_ASCII);
            int size = Integer.parseInt(sizeStr);
            if(size != raw.length- y - 1){
                throw new RuntimeException("malformed object "+sha+": bad length");
            }

            Class<? extends GitObject> c = null;
            switch (fmt){
                // case "commit" -> c = GitCommit.class;
                //case "tree" -> c = GitTree.class;
                //case "tag" -> c = GitTag.class;
                case "blob" -> c = GitBlob.class;
                default -> System.out.println("idk man");
            }

            byte[] data = new byte[size];
            System.arraycopy(raw, y+1, data, 0, size);
            assert c != null;
            return c.getDeclaredConstructor(byte[].class).newInstance((Object) data);
        }
        catch (Exception e){
            e.getMessage();
        }

        return null;
    }

    public String objectWrite(GitObject obj) throws IOException, NoSuchAlgorithmException {
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
            }
        }

        return sha;
    }

    private String computeSHA(byte[] data) throws NoSuchAlgorithmException {
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

    private int indexOf(byte[] array, byte target) {
        return indexOf(array, target, 0);
    }

    private int indexOf(byte[] array, byte target, int start) {
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

                writeFile(new String[]{"HEADS"}, "ref: refs/heads/master\n"); //master as linus intended
                writeFile(new String[]{"description"}, "unnamed repo, edit this file to name repo.\n");
                writeFile(new String[]{"config"}, repoDefaultConfig().toString());

                System.out.println("gitz repo initialized");
            }

            else {
                System.out.println("failed to init gitz repo");
            }
        }
    }

    // computes the path
    public Path repoPath(String... path){
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

    public Path repoFind(String path, boolean required){
        File dir = new File(path).getAbsoluteFile();

        if(new File(dir, ".gitz").isDirectory()){
            return dir.toPath();
        }

        File parent = dir.getParentFile();

        if(parent == null || dir.equals(parent)){
            if(required)
                throw new RuntimeException("not a gitz dir");
            else
                return null;
        }

        return repoFind(parent.getAbsolutePath(), required);
    }
}