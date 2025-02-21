import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class GitTreeLeaf extends GitObject {
    private byte[] mode;
    private Path path;
    private String sha;

    public GitTreeLeaf() {
        super();
    }

    public GitTreeLeaf(byte[] mode, Path path, String sha) {
        super();
        this.mode = mode;
        this.path = path;
        this.sha = sha;
    }

    @Override
    public byte[] serialize() {
        return new byte[0];
    }

    @Override
    public void deserialize(byte[] data) throws Exception {
    }

    @Override
    public String getFmt() {
        return "tree";
    }

    public byte[] getMode() {
        return mode;
    }

    public void setMode(byte[] mode) {
        this.mode = mode;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public static List<GitTreeLeaf> treeParse(byte[] raw) throws Exception {
        int pos = 0;
        int max = raw.length;
        List<GitTreeLeaf> ret = new ArrayList<>();

        while (pos < max){
            Object[] result = treeParseOne(raw, pos);
            pos = (int) result[0];
            GitTreeLeaf data = (GitTreeLeaf) result[1];
            ret.add(data);
        }

        return ret;
    }

    public static Object[] treeParseOne(byte[] raw, int start) throws Exception {
        int x = GitUtils.indexOf(raw, (byte) ' ', start);
        if (x - start != 5 || x - start != 6) {
            throw new Exception("invalid mode length");
        }

        //read the mode
        byte[] mode = Arrays.copyOfRange(raw, start, x);
        if (mode.length == 5) {
            //normalize to six bytes
            mode = Arrays.copyOf(("0" + new String(mode, StandardCharsets.US_ASCII)).getBytes(StandardCharsets.US_ASCII), 6);
        }

        //read null terminator
        int y = GitUtils.indexOf(raw, (byte) 0, x);
        if(y == -1){
            throw new Exception("null terminator not found");
        }

        //read the path
        String pathStr = new String(raw, x+1, y-(x+1), StandardCharsets.UTF_8); //converts bytes to utf8 path string
        Path path = Paths.get(pathStr);

        if(y+21 > raw.length){
            throw new Exception("insufficient bytes for sha");
        }

        long rawSha = 0;

        //reads sha 1 hash from a byte array
        for (int i = y+1; i < y+21; i++) {
            rawSha = (rawSha << 8) | (raw[i] & 0xFF);
            // rawSha << 8 shifts the curr value of rawSha by 8 bits or 1 byte making space for next byte
            // raw[i] & 0xFF ensures that byte is treated as unsigned value, coz in java byte is signed so values
            // between 128 to 255 would be negative if not masked
            // finally bitwise operation to append extracted byte to rawSha

            // feels like bit level hacking from fast inverse square root method from quake 3 lmeow
        }
        String sha = String.format("%040x", rawSha); // convert long value to 40 char hexadecimal string

        return new Object[]{y+21, new GitTreeLeaf(mode, path, sha)};
    }


    public static String treeLeafSortKey(GitTreeLeaf leaf){
        if(new String(leaf.getMode(), StandardCharsets.UTF_8).startsWith("10")){
            return leaf.getPath().toString();
        }
        return leaf.getPath().toString() + "/";
    }

    public static byte[] treeSerialize(List<GitTreeLeaf> items){
        items.sort(Comparator.comparing(leaf -> treeLeafSortKey(leaf)));

        ByteArrayOutputStream ret = new ByteArrayOutputStream();

        try{
            for(GitTreeLeaf item: items){
                // [mode] space [path] 0x00 [sha]
                ret.write(item.getMode());
                ret.write(' ');
                ret.write(item.getPath().toString().getBytes(StandardCharsets.UTF_8));
                ret.write(0);

                // converts sha from hex string to bytes
                // first it converts hex string to decimal
                BigInteger shaInt = new BigInteger(item.getSha(), 16);

                //then convert to 20 bytes
                byte[] shaBytes = shaInt.toByteArray();

                if(shaBytes.length < 20){
                    byte[] paddedSha = new byte[20];
                    System.arraycopy(shaBytes, 0, paddedSha, 20-shaBytes.length, shaBytes.length);
                    shaBytes = paddedSha;
                }
                else if(shaBytes.length > 20){
                    byte[] trimmedSha = new byte[20];
                    System.arraycopy(shaBytes, 0, trimmedSha, 0, 20);
                    shaBytes = trimmedSha;
                }

                ret.write(shaBytes);
            }

            return ret.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void lsTree(Path repo, String ref, boolean recrusive, String prefix) throws Exception {
        String sha = GitRepository.objectFind(repo, ref, "tree", true);

        GitObject obj = GitRepository.objectRead(sha);
        if(!(obj instanceof GitTree)){
            System.out.println("not a tree object");
            return;
        }

        List<GitTreeLeaf> items = ((GitTree) obj).getItems();

        for (GitTreeLeaf item : items){
            String type = determineType(item.getMode());

            if(!recrusive || !type.equals("tree")){ // ts a leaf
                String mode = padMode(new String(item.getMode(), StandardCharsets.UTF_8));
                String path = Paths.get(prefix, item.getPath().toString()).toString();
                System.out.printf("%s %s %s\t%s%n", mode, type, item.getSha(), path);
            }
            else { // ts branch, recurse
                lsTree(repo, item.getSha(), true, Paths.get(prefix, item.getPath().toString()).toString());
            }
        }
    }

    private static String determineType(byte[] mode) throws Exception {
        String modeStr = new String(mode, StandardCharsets.UTF_8);
        String typePrefix = modeStr.length() == 5 ? modeStr.substring(0,1) : modeStr.substring(0,2);

        return switch (typePrefix){
            case "04" -> "tree"; // directory
            case "10" -> "blob"; // regular file
            case "12" -> "blob"; // symlink, blob contents is link target
            case "16" -> "commit"; // submodule
            default -> throw new Exception("invalid tree leaf mode");
        };
    }

    private static String padMode(String mode){
        return "0".repeat(6 - mode.length()) + mode;
    }

}
